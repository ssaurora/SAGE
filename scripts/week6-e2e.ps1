param(
    [switch]$KeepRunning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendDir = Join-Path $root "BackEnd"
$serviceDir = Join-Path $root "Service/planning-pass1"

$postgresContainer = "sage-week6-postgres"
$redisContainer = "sage-week6-redis"
$serviceContainer = "sage-week6-service"
$backendContainer = "sage-week6-backend"
$backendImage = "sage-backend:e2e"

$backendPort = 48080
$servicePort = 48001
$postgresPort = 45432
$redisPort = 46379

$runId = Get-Date -Format "yyyyMMddHHmmss"
$backendLogOut = Join-Path $PSScriptRoot "week6-backend.stdout.$runId.log"
$backendLogErr = Join-Path $PSScriptRoot "week6-backend.stderr.$runId.log"
$diagnosticLog = Join-Path $PSScriptRoot "week6-diagnostic.$runId.log"

$backendProcess = $null
$uploadTempFile = Join-Path $PSScriptRoot "week6-upload-precipitation.txt"

function Wait-Until {
    param(
        [scriptblock]$Probe,
        [int]$TimeoutSeconds = 120,
        [string]$ErrorMessage = "Timeout"
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastError = $null
    while ((Get-Date) -lt $deadline) {
        try {
            if (& $Probe) { return }
        } catch {
            $lastError = $_
        }
        Start-Sleep -Seconds 2
    }
    if ($null -ne $lastError) {
        throw "$ErrorMessage`nLast probe error: $($lastError.Exception.Message)"
    }
    throw $ErrorMessage
}

function Test-HttpOk {
    param([string[]]$Urls)

    foreach ($url in $Urls) {
        try {
            $resp = & curl.exe -s --max-time 2 $url
            if (-not [string]::IsNullOrWhiteSpace($resp)) {
                $json = $resp | ConvertFrom-Json
                if ($json.status -eq "ok" -or $json.status -eq "UP") {
                    return $true
                }
            }
        } catch {
            Write-Warning "Health probe failed for ${url}: $($_.Exception.Message)"
        }
    }
    return $false
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw "Assertion failed: $Message"
    }
}

function Remove-ContainerIfExists {
    param([string]$Name)
    $containerId = (docker ps -aq --filter "name=^$Name$")
    if ($containerId) {
        docker rm -f $Name | Out-Null
    }
}

function Stop-ProcessListeningOnPort {
    param([int]$Port)
    $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($listeners) {
        $pids = $listeners | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($owningPid in $pids) {
            try { Stop-Process -Id $owningPid -Force -ErrorAction Stop } catch {}
        }
    }
}

function Start-BackendContainer {
    param(
        [string]$BackendDir,
        [string]$ImageTag,
        [string]$ContainerName,
        [int]$HostPort,
        [int]$PostgresPort,
        [int]$ServicePort,
        [string]$JwtSecret
    )

    Push-Location $BackendDir
    try {
        $packageResult = Invoke-NativeCapture -FilePath "mvn" -Arguments @("-q", "-DskipTests", "package")
        if ($packageResult.ExitCode -ne 0) {
            throw "failed to package backend jar: $($packageResult.Output)"
        }

        $buildResult = Invoke-NativeCapture -FilePath "docker" -Arguments @("build", "-t", $ImageTag, ".")
        if ($buildResult.ExitCode -ne 0) {
            throw "failed to build backend image: $($buildResult.Output)"
        }
    }
    finally {
        Pop-Location
    }

    Remove-ContainerIfExists -Name $ContainerName

    $runResult = Invoke-NativeCapture -FilePath "docker" -Arguments @(
        "run", "--name", $ContainerName, "--rm", "-d",
        "-p", "${HostPort}:8080",
        "-e", "DB_URL=jdbc:postgresql://host.docker.internal:${PostgresPort}/sage",
        "-e", "DB_USERNAME=postgres",
        "-e", "DB_PASSWORD=postgres",
        "-e", "PASS1_BASE_URL=http://host.docker.internal:${ServicePort}",
        "-e", "SERVER_PORT=8080",
        "-e", "JWT_SECRET=${JwtSecret}",
        $ImageTag
    )
    if ($runResult.ExitCode -ne 0) {
        throw "failed to start backend container: $($runResult.Output)"
    }

    return $runResult.Output.Trim()
}

function Invoke-NativeCapture {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )

    $tempOut = [System.IO.Path]::GetTempFileName()
    $tempErr = [System.IO.Path]::GetTempFileName()
    try {
        $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments -NoNewWindow -PassThru -Wait `
            -RedirectStandardOutput $tempOut -RedirectStandardError $tempErr
        $stdout = if (Test-Path $tempOut) { Get-Content -Raw $tempOut } else { "" }
        $stderr = if (Test-Path $tempErr) { Get-Content -Raw $tempErr } else { "" }
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Output = ($stdout + "`n" + $stderr)
        }
    }
    finally {
        if (Test-Path $tempOut) { Remove-Item $tempOut -Force -ErrorAction SilentlyContinue }
        if (Test-Path $tempErr) { Remove-Item $tempErr -Force -ErrorAction SilentlyContinue }
    }
}

function Invoke-CurlJson {
    param(
        [string]$Method,
        [string]$Uri,
        [string[]]$Headers,
        [string]$Body
    )

    $headerMap = @{}
    foreach ($header in $Headers) {
        $parts = $header -split ":", 2
        if ($parts.Length -eq 2) {
            $headerMap[$parts[0].Trim()] = $parts[1].Trim()
        }
    }

    $contentType = $null
    if ($headerMap.ContainsKey("Content-Type")) {
        $contentType = $headerMap["Content-Type"]
        $headerMap.Remove("Content-Type") | Out-Null
    }

    try {
        $invokeArgs = @{
            Method = $Method
            Uri = $Uri
            Headers = $headerMap
        }

        if ($null -ne $contentType) {
            $invokeArgs["ContentType"] = $contentType
        }

        if (-not [string]::IsNullOrWhiteSpace($Body)) {
            $invokeArgs["Body"] = $Body
        }

        try {
            $response = Invoke-WebRequest @invokeArgs
            $statusCode = [int]$response.StatusCode
            $content = [string]$response.Content
        }
        catch {
            $exception = $_.Exception
            $response = $exception.Response
            if ($null -eq $response) {
                throw
            }

            $statusCode = [int]$response.StatusCode
            $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
            try {
                $content = $reader.ReadToEnd()
            }
            finally {
                $reader.Dispose()
            }
        }

        $json = $null
        if (-not [string]::IsNullOrWhiteSpace($content)) {
            try { $json = $content | ConvertFrom-Json } catch {}
        }
        return [pscustomobject]@{
            StatusCode = $statusCode
            Body = $content
            Json = $json
        }
    }
    finally {}
}

function Get-WaitingContext {
    param($TaskSnapshot)
    if ($null -eq $TaskSnapshot) { return $null }
    if ($TaskSnapshot -is [System.Collections.IDictionary]) {
        if ($TaskSnapshot.Contains("waiting_context")) { return $TaskSnapshot["waiting_context"] }
        if ($TaskSnapshot.Contains("waitingContext")) { return $TaskSnapshot["waitingContext"] }
    }
    $prop = $TaskSnapshot.PSObject.Properties["waiting_context"]
    if ($null -ne $prop) { return $prop.Value }
    $camelProp = $TaskSnapshot.PSObject.Properties["waitingContext"]
    if ($null -ne $camelProp) { return $camelProp.Value }
    return $null
}

function Get-CanResumeValue {
    param($WaitingContext)
    if ($null -eq $WaitingContext) { return $null }
    if ($WaitingContext -is [System.Collections.IDictionary]) {
        if ($WaitingContext.Contains("can_resume")) { return [bool]$WaitingContext["can_resume"] }
        if ($WaitingContext.Contains("canResume")) { return [bool]$WaitingContext["canResume"] }
    }
    $snake = $WaitingContext.PSObject.Properties["can_resume"]
    if ($null -ne $snake) { return [bool]$snake.Value }
    $camel = $WaitingContext.PSObject.Properties["canResume"]
    if ($null -ne $camel) { return [bool]$camel.Value }
    return $null
}

function Wait-TaskState {
    param(
        [string]$TaskId,
        [hashtable]$Headers,
        [string[]]$States,
        [int]$TimeoutSeconds = 120
    )

    $snapshot = $null
    Wait-Until -TimeoutSeconds $TimeoutSeconds -ErrorMessage "task $TaskId did not reach expected state" -Probe {
        $script:snapshot = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$TaskId" -Headers $Headers
        return $States -contains $script:snapshot.state
    }
    return $script:snapshot
}

$envVars = @(
    "DB_URL", "DB_USERNAME", "DB_PASSWORD", "PASS1_BASE_URL", "SERVER_PORT", "JWT_SECRET"
)
$envBackup = @{}
foreach ($name in $envVars) {
    $envBackup[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

try {
    Write-Output "[1/10] Start PostgreSQL"
    Remove-ContainerIfExists -Name $postgresContainer
    docker run --name $postgresContainer --rm -d -p ${postgresPort}:5432 -e POSTGRES_DB=sage -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:17 | Out-Null
    Wait-Until -TimeoutSeconds 90 -ErrorMessage "PostgreSQL startup timed out" -Probe {
        docker exec $postgresContainer pg_isready -U postgres -d sage | Out-Null
        return ($LASTEXITCODE -eq 0)
    }

    Write-Output "[2/10] Start Redis"
    Remove-ContainerIfExists -Name $redisContainer
    docker run --name $redisContainer --rm -d -p ${redisPort}:6379 redis:7-alpine | Out-Null
    Wait-Until -TimeoutSeconds 60 -ErrorMessage "Redis startup timed out" -Probe {
        $pong = docker exec $redisContainer redis-cli ping
        return ($LASTEXITCODE -eq 0 -and ($pong -join "") -match "PONG")
    }

    Write-Output "[3/10] Start Service container"
    Remove-ContainerIfExists -Name $serviceContainer
    Push-Location $serviceDir
    try {
        $buildResult = Invoke-NativeCapture -FilePath "docker" -Arguments @("build", "-t", "sage-pass1:week6", ".")
        if ($buildResult.ExitCode -ne 0) {
            throw "failed to build service image: $($buildResult.Output)"
        }
    } finally {
        Pop-Location
    }
    $serviceRun = Invoke-NativeCapture -FilePath "docker" -Arguments @("run", "--name", $serviceContainer, "--rm", "-d", "-p", "${servicePort}:8001", "-e", "REDIS_ENABLED=true", "-e", "REDIS_URL=redis://host.docker.internal:${redisPort}/0", "sage-pass1:week6")
    if ($serviceRun.ExitCode -ne 0) {
        throw "failed to start service container: $($serviceRun.Output)"
    }
    Wait-Until -TimeoutSeconds 120 -ErrorMessage "Service startup timed out" -Probe {
        Test-HttpOk -Urls @("http://localhost:${servicePort}/health", "http://127.0.0.1:${servicePort}/health")
    }

    Write-Output "[4/10] Start BackEnd"
    Stop-ProcessListeningOnPort -Port $backendPort
    $backendContainerId = Start-BackendContainer -BackendDir $backendDir -ImageTag $backendImage -ContainerName $backendContainer -HostPort $backendPort -PostgresPort $postgresPort -ServicePort $servicePort -JwtSecret "sage-week6-e2e-secret-key-123456789"

    Wait-Until -TimeoutSeconds 150 -ErrorMessage "BackEnd startup timed out" -Probe {
        $backendInspect = Invoke-NativeCapture -FilePath "docker" -Arguments @("inspect", "-f", "{{.State.Running}}", $backendContainer)
        if ($backendInspect.ExitCode -ne 0 -or $backendInspect.Output.Trim() -ne "true") {
            $backendLogs = Invoke-NativeCapture -FilePath "docker" -Arguments @("logs", $backendContainer)
            throw "BackEnd container exited early. logs: $($backendLogs.Output)"
        }
        Test-HttpOk -Urls @("http://localhost:${backendPort}/actuator/health", "http://127.0.0.1:${backendPort}/actuator/health")
    }

    $loginBody = @{ username = "demo"; password = "demo123" } | ConvertTo-Json
    $loginResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/auth/login" -ContentType "application/json" -Body $loginBody
    $accessToken = ([string]$loginResponse.access_token).Trim()
    Assert-True (-not [string]::IsNullOrWhiteSpace($accessToken)) "login token should exist"
    $headers = @{ Authorization = "Bearer $accessToken" }

    $meResponse = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/auth/me" -Headers $headers
    Assert-True ([string]$meResponse.username -eq "demo") "authenticated /auth/me should return demo"

    Write-Output "[5/10] Success path"
    $successBody = @{ user_query = "Run week4 success chain" } | ConvertTo-Json
    $successTask = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks" -Headers $headers -ContentType "application/json" -Body $successBody
    $successTaskId = [string]$successTask.task_id
    Assert-True (-not [string]::IsNullOrWhiteSpace($successTaskId)) "success task id should exist"
    $successFinal = Wait-TaskState -TaskId $successTaskId -Headers $headers -States @("SUCCEEDED", "FAILED", "CANCELLED") -TimeoutSeconds 120
    Assert-True ($successFinal.state -eq "SUCCEEDED") "success path should reach SUCCEEDED"

    $successDetail = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$successTaskId" -Headers $headers
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$successDetail.latest_result_bundle_id)) "latest_result_bundle_id should exist"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$successDetail.latest_workspace_id)) "latest_workspace_id should exist"

    $successManifest = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$successTaskId/manifest" -Headers $headers
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$successManifest.manifest_id)) "manifest_id should exist"
    Assert-True ($null -ne $successManifest.goal_parse) "goal_parse should exist"
    Assert-True ($null -ne $successManifest.skill_route) "skill_route should exist"
    Assert-True ($null -ne $successManifest.runtime_assertions) "runtime_assertions should exist"

    $successResult = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$successTaskId/result" -Headers $headers
    Assert-True ($null -ne $successResult.result_bundle) "result_bundle should exist"
    Assert-True ($null -ne $successResult.final_explanation) "final_explanation should exist"
    Assert-True ($null -ne $successResult.workspace_summary) "workspace_summary should exist"
    Assert-True ($null -ne $successResult.artifact_catalog) "artifact_catalog should exist"
    Assert-True ($null -ne $successResult.result_bundle.metrics.water_yield_index) "water_yield_index should exist"
    Assert-True ($null -ne $successResult.result_bundle.metrics.climate_balance) "climate_balance should exist"

    $successRuns = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$successTaskId/runs" -Headers $headers
    Assert-True (@($successRuns.items).Count -ge 1) "success runs should exist"

    $successArtifacts = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$successTaskId/artifacts" -Headers $headers
    Assert-True (@($successArtifacts.items).Count -ge 1) "success artifacts should exist"
    $successAttempt = @($successArtifacts.items)[0]
    Assert-True (@($successAttempt.artifacts.primary_outputs).Count -ge 1) "primary_outputs should exist"
    Assert-True (@($successAttempt.artifacts.logs).Count -ge 1) "logs should exist"
    Assert-True ($successAttempt.workspace.workspace_state -in @("CLEANED", "ARCHIVED", "FAILED_CLEANUP")) "workspace_state should be terminal"

    Write-Output "[6/10] Repair + resume path"
    $repairBody = @{ user_query = "week5 missing precipitation input" } | ConvertTo-Json
    $repairTask = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks" -Headers $headers -ContentType "application/json" -Body $repairBody
    $repairTaskId = [string]$repairTask.task_id
    Assert-True (-not [string]::IsNullOrWhiteSpace($repairTaskId)) "repair task id should exist"
    $repairWaiting = Wait-TaskState -TaskId $repairTaskId -Headers $headers -States @("WAITING_USER") -TimeoutSeconds 60
    $waitingContext = Get-WaitingContext -TaskSnapshot $repairWaiting
    Assert-True ((Get-CanResumeValue -WaitingContext $waitingContext) -eq $false) "can_resume should be false before upload"

    Set-Content -Path $uploadTempFile -Value "fake precipitation raster" -Encoding UTF8
    $uploadRaw = curl.exe -s -X POST "http://localhost:${backendPort}/tasks/$repairTaskId/attachments" -H "Authorization: Bearer $accessToken" -F "file=@$uploadTempFile" -F "logical_slot=precipitation"
    $uploadResponse = $uploadRaw | ConvertFrom-Json
    Assert-True ($uploadResponse.assignment_status -eq "ASSIGNED") "repair attachment should be assigned"

    Wait-Until -TimeoutSeconds 40 -ErrorMessage "waiting_context did not become resumable" -Probe {
        $snapshot = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$repairTaskId" -Headers $headers
        $ctx = Get-WaitingContext -TaskSnapshot $snapshot
        (Get-CanResumeValue -WaitingContext $ctx) -eq $true
    }

    $resumeBody = @{ resume_request_id = [guid]::NewGuid().ToString(); user_note = "uploaded precipitation" } | ConvertTo-Json
    $resumeResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks/$repairTaskId/resume" -Headers $headers -ContentType "application/json" -Body $resumeBody
    Assert-True ($resumeResponse.resume_accepted -eq $true) "resume should be accepted"

    $repairFinal = Wait-TaskState -TaskId $repairTaskId -Headers $headers -States @("SUCCEEDED", "FAILED", "CANCELLED") -TimeoutSeconds 120
    Assert-True ($repairFinal.state -in @("SUCCEEDED", "FAILED", "CANCELLED")) "repair task should be terminal"

    $repairRuns = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$repairTaskId/runs" -Headers $headers
    $repairAttempts = @($repairRuns.items | ForEach-Object { $_.attempt_no })
    Assert-True (@($repairRuns.items).Count -ge 2) "repair path should record at least two attempts"
    Assert-True ($repairAttempts -contains 1) "repair path should include attempt 1"
    Assert-True ($repairAttempts -contains 2) "repair path should include attempt 2"
    $repairRun2 = @($repairRuns.items | Where-Object { $_.attempt_no -eq 2 })[0]
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$repairRun2.workspace_id)) "attempt 2 workspace_id should exist"

    $repairArtifacts = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$repairTaskId/artifacts" -Headers $headers
    $repairArtifactAttempt2 = @($repairArtifacts.items | Where-Object { $_.attempt_no -eq 2 })[0]
    Assert-True ($null -ne $repairArtifactAttempt2) "attempt 2 artifacts should exist"
    Assert-True (@($repairArtifactAttempt2.artifacts.logs).Count -ge 1) "attempt 2 logs should exist"

    Write-Output "[7/10] Fatal validation path"
    $fatalBody = @{ user_query = "week5 invalidbinding demo" } | ConvertTo-Json
    $fatalTask = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks" -Headers $headers -ContentType "application/json" -Body $fatalBody
    $fatalTaskId = [string]$fatalTask.task_id
    Assert-True (-not [string]::IsNullOrWhiteSpace($fatalTaskId)) "fatal task id should exist"
    $fatalFinal = Wait-TaskState -TaskId $fatalTaskId -Headers $headers -States @("FAILED") -TimeoutSeconds 60
    Assert-True ($fatalFinal.state -eq "FAILED") "fatal path should reach FAILED"
    $fatalResult = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$fatalTaskId/result" -Headers $headers
    Assert-True ($null -ne $fatalResult.failure_summary) "fatal failure_summary should exist"
    Assert-True ($fatalResult.failure_summary.failure_code -eq "FATAL_VALIDATION") "fatal failure_code should be FATAL_VALIDATION"
    $fatalRuns = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$fatalTaskId/runs" -Headers $headers
    Assert-True (@($fatalRuns.items).Count -ge 1) "fatal path should record attempt history"

    Write-Output "[8/10] Cancel path with Redis coordination"
    $cancelBody = @{ user_query = "Run week4 cancel chain" } | ConvertTo-Json
    $cancelTask = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks" -Headers $headers -ContentType "application/json" -Body $cancelBody
    $cancelTaskId = [string]$cancelTask.task_id
    Assert-True (-not [string]::IsNullOrWhiteSpace($cancelTaskId)) "cancel task id should exist"

    Wait-Until -TimeoutSeconds 60 -ErrorMessage "cancel task did not get a job" -Probe {
        $snapshot = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$cancelTaskId" -Headers $headers
        return $null -ne $snapshot.job -and -not [string]::IsNullOrWhiteSpace([string]$snapshot.job.job_id)
    }
    $cancelSnapshot = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$cancelTaskId" -Headers $headers
    $cancelJobId = [string]$cancelSnapshot.job.job_id

    $cancelStatus = 0
    try {
        $cancelResponse = Invoke-WebRequest -Method Post -Uri "http://localhost:${backendPort}/tasks/$cancelTaskId/cancel" -Headers $headers -ContentType "application/json" -Body "{}"
        $cancelStatus = [int]$cancelResponse.StatusCode
    }
    catch {
        if ($null -eq $_.Exception.Response) {
            throw
        }
        $cancelStatus = [int]$_.Exception.Response.StatusCode
    }
    Assert-True ($cancelStatus -eq 202 -or $cancelStatus -eq 409) "cancel should return 202 or 409, got $cancelStatus"

    Wait-Until -TimeoutSeconds 30 -ErrorMessage "redis cancel token was not written" -Probe {
        $tokenValue = docker exec $redisContainer redis-cli GET "sage:job:${cancelJobId}:cancel"
        return ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace(($tokenValue -join "").Trim()))
    }

    $cancelFinal = Wait-TaskState -TaskId $cancelTaskId -Headers $headers -States @("CANCELLED", "SUCCEEDED", "FAILED") -TimeoutSeconds 120
    Assert-True ($cancelFinal.state -in @("CANCELLED", "SUCCEEDED", "FAILED")) "cancel task should be terminal"
    $cancelResult = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$cancelTaskId/result" -Headers $headers
    if ($cancelFinal.state -in @("CANCELLED", "FAILED")) {
        Assert-True ($null -ne $cancelResult.failure_summary) "cancelled/failed task should have failure_summary"
    }
    $cancelArtifacts = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$cancelTaskId/artifacts" -Headers $headers
    Assert-True (@($cancelArtifacts.items).Count -ge 1) "cancel path should retain artifact/workspace records"
    $cancelAttempt = @($cancelArtifacts.items)[0]
    Assert-True (@($cancelAttempt.artifacts.logs).Count -ge 1) "cancel path logs should exist"

    Write-Output "[9/10] Event spot check"
    $repairEvents = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$repairTaskId/events" -Headers $headers
    $repairEventTypes = @($repairEvents.items | ForEach-Object { $_.event_type })
    Assert-True ($repairEventTypes -contains "WAITING_USER_ENTERED") "repair events should include WAITING_USER_ENTERED"
    Assert-True ($repairEventTypes -contains "RESUME_ACCEPTED") "repair events should include RESUME_ACCEPTED"

    Write-Output "[10/10] Week6 E2E passed"
    Write-Output "success: $successTaskId => $($successFinal.state)"
    Write-Output "repair:  $repairTaskId => $($repairFinal.state)"
    Write-Output "fatal:   $fatalTaskId => $($fatalFinal.state)"
    Write-Output "cancel:  $cancelTaskId => $($cancelFinal.state)"

    if ($KeepRunning) {
        Write-Output "KeepRunning=true, leaving services alive"
        $backendProcess = $null
    }
}
catch {
    $diagnostic = @()
    $diagnostic += ("failure_time=" + (Get-Date).ToString("o"))
    $diagnostic += ("message=" + $_.Exception.Message)
    $diagnostic += "--- exception ---"
    $diagnostic += ($_ | Format-List * -Force | Out-String)

    $backendLog = Invoke-NativeCapture -FilePath "docker" -Arguments @("logs", $backendContainer)
    if ($backendLog.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($backendLog.Output)) {
        $diagnostic += "--- backend container logs ---"
        $diagnostic += (($backendLog.Output -split "`r?`n") | Select-Object -Last 80)
    }

    $serviceLog = Invoke-NativeCapture -FilePath "docker" -Arguments @("logs", $serviceContainer)
    if ($serviceLog.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($serviceLog.Output)) {
        $diagnostic += "--- service container logs ---"
        $diagnostic += (($serviceLog.Output -split "`r?`n") | Select-Object -Last 80)
    }

    $diagnostic | Set-Content -Path $diagnosticLog -Encoding UTF8

    Write-Error ("Week6 E2E failed: " + $_.Exception.Message)

    $backendLog = Invoke-NativeCapture -FilePath "docker" -Arguments @("logs", $backendContainer)
    if ($backendLog.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($backendLog.Output)) {
        Write-Host "--- backend container logs ---"
        ($backendLog.Output -split "`r?`n") | Select-Object -Last 80
    }

    $serviceLog = Invoke-NativeCapture -FilePath "docker" -Arguments @("logs", $serviceContainer)
    if ($serviceLog.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($serviceLog.Output)) {
        Write-Host "--- service container logs ---"
        ($serviceLog.Output -split "`r?`n") | Select-Object -Last 80
    }

    throw
}
finally {
    foreach ($name in $envVars) {
        [Environment]::SetEnvironmentVariable($name, $envBackup[$name], "Process")
    }

    if (Test-Path $uploadTempFile) {
        Remove-Item $uploadTempFile -Force -ErrorAction SilentlyContinue
    }

    Stop-ProcessListeningOnPort -Port $backendPort

    if (-not $KeepRunning) {
        Remove-ContainerIfExists -Name $backendContainer
        Remove-ContainerIfExists -Name $serviceContainer
        Remove-ContainerIfExists -Name $redisContainer
        Remove-ContainerIfExists -Name $postgresContainer
    }
}
