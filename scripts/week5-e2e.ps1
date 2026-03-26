param(
    [switch]$KeepRunning,
    [switch]$AllowLocalServiceFallback = $false
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendDir = Join-Path $root "BackEnd"
$serviceDir = Join-Path $root "Service/planning-pass1"

$postgresContainer = "sage-week5-postgres"
$serviceContainer = "sage-week5-service"
$backendContainer = "sage-week5-backend"
$backendImage = "sage-backend:week5"

$backendPort = 38080
$servicePort = 38001
$postgresPort = 35432

$runId = Get-Date -Format "yyyyMMddHHmmss"
$backendLogOut = Join-Path $PSScriptRoot "week5-backend.stdout.$runId.log"
$backendLogErr = Join-Path $PSScriptRoot "week5-backend.stderr.$runId.log"
$serviceLogOut = Join-Path $PSScriptRoot "week5-service.stdout.$runId.log"
$serviceLogErr = Join-Path $PSScriptRoot "week5-service.stderr.$runId.log"

$backendProcess = $null
$serviceProcess = $null

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
    param(
        [string[]]$Urls
    )

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
    } finally {
        if (Test-Path $tempOut) { Remove-Item $tempOut -Force -ErrorAction SilentlyContinue }
        if (Test-Path $tempErr) { Remove-Item $tempErr -Force -ErrorAction SilentlyContinue }
    }
}

function Invoke-NativeCaptureWithRetry {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [int]$MaxAttempts = 2,
        [int]$SleepSeconds = 5
    )

    $last = $null
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $last = Invoke-NativeCapture -FilePath $FilePath -Arguments $Arguments
        if ($last.ExitCode -eq 0) {
            return $last
        }
        if ($attempt -lt $MaxAttempts) {
            Write-Warning "Native command failed (attempt $attempt/$MaxAttempts), retrying in ${SleepSeconds}s..."
            Start-Sleep -Seconds $SleepSeconds
        }
    }
    return $last
}

function Invoke-WebJson {
    param(
        [string]$Method,
        [string]$Uri,
        [string[]]$Headers,
        [string]$Body = ""
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

function Start-LocalServiceFallback {
    param(
        [string]$ServiceDir,
        [int]$Port,
        [string]$StdOutLog,
        [string]$StdErrLog
    )

    Stop-ProcessListeningOnPort -Port $Port

    $condaCommand = Get-Command conda -ErrorAction SilentlyContinue
    if ($null -ne $condaCommand) {
        return Start-Process -FilePath $condaCommand.Source `
            -ArgumentList "run", "-n", "sage-cognitive", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "${Port}" `
            -WorkingDirectory $ServiceDir `
            -RedirectStandardOutput $StdOutLog `
            -RedirectStandardError $StdErrLog `
            -PassThru
    }

    $pythonCommand = Get-Command python -ErrorAction SilentlyContinue
    if ($null -ne $pythonCommand) {
        return Start-Process -FilePath $pythonCommand.Source `
            -ArgumentList "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "${Port}" `
            -WorkingDirectory $ServiceDir `
            -RedirectStandardOutput $StdOutLog `
            -RedirectStandardError $StdErrLog `
            -PassThru
    }

    throw "Local fallback failed: neither 'conda' nor 'python' command is available in PATH."
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

function Test-ContainerRunning {
    param([string]$Name)
    $inspect = Invoke-NativeCapture -FilePath "docker" -Arguments @("inspect", "-f", "{{.State.Running}}", $Name)
    if ($inspect.ExitCode -ne 0) {
        return $false
    }
    return $inspect.Output.Trim() -eq "true"
}

function Get-WaitingContext {
    param($TaskSnapshot)
    if ($null -eq $TaskSnapshot) {
        return $null
    }

    if ($TaskSnapshot -is [System.Collections.IDictionary]) {
        if ($TaskSnapshot.Contains("waiting_context")) {
            return $TaskSnapshot["waiting_context"]
        }
        if ($TaskSnapshot.Contains("waitingContext")) {
            return $TaskSnapshot["waitingContext"]
        }
    }

    $prop = $TaskSnapshot.PSObject.Properties["waiting_context"]
    if ($null -ne $prop) {
        return $prop.Value
    }

    $camelProp = $TaskSnapshot.PSObject.Properties["waitingContext"]
    if ($null -ne $camelProp) {
        return $camelProp.Value
    }

    return $null
}

function Get-CanResumeValue {
    param($WaitingContext)
    if ($null -eq $WaitingContext) {
        return $null
    }

    if ($WaitingContext -is [System.Collections.IDictionary]) {
        if ($WaitingContext.Contains("can_resume")) {
            return [bool]$WaitingContext["can_resume"]
        }
        if ($WaitingContext.Contains("canResume")) {
            return [bool]$WaitingContext["canResume"]
        }
    }

    $snake = $WaitingContext.PSObject.Properties["can_resume"]
    if ($null -ne $snake) {
        return [bool]$snake.Value
    }

    $camel = $WaitingContext.PSObject.Properties["canResume"]
    if ($null -ne $camel) {
        return [bool]$camel.Value
    }

    return $null
}

$envVars = @(
    "DB_URL", "DB_USERNAME", "DB_PASSWORD", "PASS1_BASE_URL", "SERVER_PORT", "JWT_SECRET"
)
$envBackup = @{}
foreach ($name in $envVars) {
    $envBackup[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

$uploadTempFile = Join-Path $PSScriptRoot "week5-upload-precipitation.txt"

try {
    Write-Output ("[mode] strict_docker={0}, local_fallback={1}" -f ($(if ($AllowLocalServiceFallback) {"false"} else {"true"}), $AllowLocalServiceFallback.IsPresent))

    Write-Output "[1/9] Start PostgreSQL"
    Remove-ContainerIfExists -Name $postgresContainer
    docker run --name $postgresContainer --rm -d -p ${postgresPort}:5432 -e POSTGRES_DB=sage -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:17 | Out-Null

    Wait-Until -TimeoutSeconds 90 -ErrorMessage "PostgreSQL startup timed out" -Probe {
        docker exec $postgresContainer pg_isready -U postgres -d sage | Out-Null
        return ($LASTEXITCODE -eq 0)
    }

    Write-Output "[2/9] Start Service container"
    Remove-ContainerIfExists -Name $serviceContainer
    $serviceBaseUrl = "http://localhost:${servicePort}"
    $serviceStartedByDocker = $false

    Push-Location $serviceDir
    try {
        $buildResult = Invoke-NativeCaptureWithRetry -FilePath "docker" -Arguments @("build", "-t", "sage-pass1:week5", ".") -MaxAttempts 2 -SleepSeconds 5
    } finally {
        Pop-Location
    }

    Write-Output "Docker build exit code: $($buildResult.ExitCode)"
    if ($buildResult.ExitCode -eq 0) {
        $inspectImage = Invoke-NativeCapture -FilePath "docker" -Arguments @("image", "inspect", "sage-pass1:week5")
        if ($inspectImage.ExitCode -ne 0) {
            if (-not $AllowLocalServiceFallback) {
                throw "docker image inspect failed after successful build: $($inspectImage.Output)"
            }
            Write-Warning "docker image inspect failed, fallback to local service. detail: $($inspectImage.Output)"
        }

        $runResult = Invoke-NativeCapture -FilePath "docker" -Arguments @("run", "--name", $serviceContainer, "--rm", "-d", "-p", "${servicePort}:8001", "sage-pass1:week5")
        Write-Output "Docker run exit code: $($runResult.ExitCode)"
        if ($runResult.ExitCode -eq 0 -and $inspectImage.ExitCode -eq 0) {
            Start-Sleep -Seconds 2
            $containerInspect = Invoke-NativeCapture -FilePath "docker" -Arguments @("inspect", "-f", "{{.State.Running}}", $serviceContainer)
            if ($containerInspect.ExitCode -eq 0 -and $containerInspect.Output.Trim() -eq "true") {
                $serviceStartedByDocker = $true
                $serviceBaseUrl = "http://127.0.0.1:${servicePort}"
                Write-Output "Service startup path: docker"
            } elseif (-not $AllowLocalServiceFallback) {
                throw "service container exited early after start. inspect: $($containerInspect.Output)"
            } else {
                Write-Warning "Service container exited early, fallback to local service. detail: $($containerInspect.Output)"
            }
        } elseif (-not $AllowLocalServiceFallback) {
            throw "failed to start service container: $($runResult.Output)"
        } else {
            Write-Warning "Service container start failed, fallback to local service. detail: $($runResult.Output)"
        }
    } elseif (-not $AllowLocalServiceFallback) {
        throw "failed to build service image: $($buildResult.Output)"
    } else {
        Write-Warning "Service image build failed, fallback to local service. detail: $($buildResult.Output)"
    }

    if (-not $serviceStartedByDocker) {
        try {
            if (Test-HttpOk -Urls @("http://localhost:8001/health", "http://127.0.0.1:8001/health")) {
                $serviceBaseUrl = "http://localhost:8001"
                Write-Warning "Using existing local Service at http://localhost:8001"
            } else {
                throw "local service health response invalid"
            }
        } catch {
            $serviceProcess = Start-LocalServiceFallback -ServiceDir $serviceDir -Port $servicePort -StdOutLog $serviceLogOut -StdErrLog $serviceLogErr
            $serviceBaseUrl = "http://127.0.0.1:${servicePort}"
            Write-Output "Service startup path: local-fallback"
        }
    }

    Wait-Until -TimeoutSeconds 120 -ErrorMessage "Service startup timed out" -Probe {
        $serviceProbeUrls = @(
            "$serviceBaseUrl/health",
            "http://127.0.0.1:${servicePort}/health",
            "http://localhost:${servicePort}/health"
        )
        if (Test-HttpOk -Urls $serviceProbeUrls) {
            return $true
        }

        if ($null -ne $serviceProcess -and $serviceProcess.HasExited) {
            $serviceErr = if (Test-Path $serviceLogErr) { Get-Content -Raw $serviceLogErr } else { "(no stderr log)" }
            $serviceOut = if (Test-Path $serviceLogOut) { Get-Content -Raw $serviceLogOut } else { "(no stdout log)" }
            throw "Local service process exited early. stdout: $serviceOut ; stderr: $serviceErr"
        }

        return $false
    }

    Write-Output "[3/9] Start BackEnd"
    Stop-ProcessListeningOnPort -Port $backendPort

    $jwtSecret = "sage-week5-e2e-secret-key-123456789"
    Start-BackendContainer -BackendDir $backendDir `
        -ImageTag $backendImage `
        -ContainerName $backendContainer `
        -HostPort $backendPort `
        -PostgresPort $postgresPort `
        -ServicePort $servicePort `
        -JwtSecret $jwtSecret | Out-Null

    Wait-Until -TimeoutSeconds 150 -ErrorMessage "BackEnd startup timed out" -Probe {
        if (-not (Test-ContainerRunning -Name $backendContainer)) {
            $backendLogs = Invoke-NativeCapture -FilePath "docker" -Arguments @("logs", $backendContainer)
            throw "BackEnd container exited early. logs: $($backendLogs.Output)"
        }
        return Test-HttpOk -Urls @(
            "http://localhost:${backendPort}/actuator/health",
            "http://127.0.0.1:${backendPort}/actuator/health"
        )
    }

    Write-Output "[4/9] Login"
    $loginBody = @{ username = "demo"; password = "demo123" } | ConvertTo-Json
    $loginResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/auth/login" -ContentType "application/json" -Body $loginBody
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$loginResponse.access_token)) "login token should exist"
    $headers = @{ Authorization = "Bearer $($loginResponse.access_token)" }

    Write-Output "[5/9] Trigger WAITING_USER"
    $taskBody = @{ user_query = "week5 missing precipitation input" } | ConvertTo-Json
    $task = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks" -Headers $headers -ContentType "application/json" -Body $taskBody
    $taskId = [string]$task.task_id
    Assert-True (-not [string]::IsNullOrWhiteSpace($taskId)) "task id should exist"

    Wait-Until -TimeoutSeconds 60 -ErrorMessage "task did not enter WAITING_USER" -Probe {
        $snapshot = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$taskId" -Headers $headers
        return $snapshot.state -eq "WAITING_USER"
    }

    $waitingSnapshot = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$taskId" -Headers $headers
    $waitingContext = Get-WaitingContext -TaskSnapshot $waitingSnapshot
    if ($null -eq $waitingContext) {
        $snapshotJson = if ($null -eq $waitingSnapshot) { "<null>" } else { $waitingSnapshot | ConvertTo-Json -Depth 20 }
        throw "waiting_context missing. task payload: $snapshotJson"
    }
    $canResumeBeforeUpload = Get-CanResumeValue -WaitingContext $waitingContext
    Assert-True ($canResumeBeforeUpload -eq $false) "can_resume should be false before upload"

    Write-Output "[5.5/9] Resume should be rejected while can_resume is false"
    $preUploadResumeRequestId = [guid]::NewGuid().ToString()
    $preUploadResumeBody = @{
        resume_request_id = $preUploadResumeRequestId
        user_note = "attempt resume before upload"
    } | ConvertTo-Json
    $preUploadResume = Invoke-WebJson -Method Post -Uri "http://localhost:${backendPort}/tasks/$taskId/resume" -Headers @(
        "Authorization: Bearer $($loginResponse.access_token)",
        "Content-Type: application/json"
    ) -Body $preUploadResumeBody
    Assert-True ($preUploadResume.StatusCode -eq 409) "resume before upload should return 409"

    Write-Output "[6/9] Upload required attachment and verify waiting_context refresh"
    Set-Content -Path $uploadTempFile -Value "fake precipitation raster" -Encoding UTF8
    $uploadRaw = curl.exe -s -X POST "http://localhost:${backendPort}/tasks/$taskId/attachments" -H "Authorization: Bearer $($loginResponse.access_token)" -F "file=@$uploadTempFile" -F "logical_slot=precipitation"
    $uploadResponse = $uploadRaw | ConvertFrom-Json
    Assert-True ($uploadResponse.assignment_status -eq "ASSIGNED") "attachment should be assigned"

    Wait-Until -TimeoutSeconds 40 -ErrorMessage "waiting_context was not refreshed to resumable" -Probe {
        $afterUploadSnapshot = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$taskId" -Headers $headers
        $ctx = Get-WaitingContext -TaskSnapshot $afterUploadSnapshot
        $canResume = Get-CanResumeValue -WaitingContext $ctx
        return $null -ne $ctx -and $canResume -eq $true
    }

    Write-Output "[7/9] Resume with idempotency key"
    $resumeRequestId = [guid]::NewGuid().ToString()
    $resumeBody = @{
        resume_request_id = $resumeRequestId
        user_note = "uploaded precipitation"
    } | ConvertTo-Json
    $resumeResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks/$taskId/resume" -Headers $headers -ContentType "application/json" -Body $resumeBody
    Assert-True ($resumeResponse.resume_accepted -eq $true) "resume should be accepted"

    $resumeReplay = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks/$taskId/resume" -Headers $headers -ContentType "application/json" -Body $resumeBody
    Assert-True ($resumeReplay.resume_accepted -eq $true) "idempotent resume should return accepted"
    Assert-True ($resumeReplay.resume_attempt -eq $resumeResponse.resume_attempt) "idempotent resume attempt should be same"

    Write-Output "[8/9] Wait for terminal and validate result"
    $finalTask = $null
    for ($i = 0; $i -lt 60; $i++) {
        $finalTask = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$taskId" -Headers $headers
        if ($finalTask.state -in @("SUCCEEDED", "FAILED", "CANCELLED", "WAITING_USER")) {
            if ($finalTask.state -eq "WAITING_USER") {
                Start-Sleep -Milliseconds 700
                continue
            }
            break
        }
        Start-Sleep -Seconds 2
    }

    Assert-True ($finalTask.state -eq "SUCCEEDED" -or $finalTask.state -eq "FAILED" -or $finalTask.state -eq "CANCELLED") "task should be terminal after resume"

    $result = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$taskId/result" -Headers $headers
    if ($finalTask.state -eq "SUCCEEDED") {
        Assert-True ($null -ne $result.result_bundle) "result_bundle should exist on success"
        Assert-True ($null -ne $result.final_explanation) "final_explanation should exist on success"
    }

    $events = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$taskId/events" -Headers $headers
    $eventTypes = @($events.items | ForEach-Object { $_.event_type })
    Assert-True ($eventTypes -contains "WAITING_USER_ENTERED") "event WAITING_USER_ENTERED should exist"
    Assert-True ($eventTypes -contains "ATTACHMENT_UPLOADED") "event ATTACHMENT_UPLOADED should exist"
    Assert-True ($eventTypes -contains "RESUME_REJECTED") "event RESUME_REJECTED should exist"
    Assert-True ($eventTypes -contains "RESUME_REQUESTED") "event RESUME_REQUESTED should exist"
    Assert-True ($eventTypes -contains "RESUME_ACCEPTED") "event RESUME_ACCEPTED should exist"

    Write-Output "[9/9] Week5 E2E passed"
    Write-Output "task: $taskId => $($finalTask.state)"

    if ($KeepRunning) {
        Write-Output "KeepRunning=true, leaving containers alive"
        Write-Output "BackEnd: http://localhost:${backendPort}"
        Write-Output "Service: $serviceBaseUrl"
        Write-Output "PostgreSQL: localhost:${postgresPort}"
    }
}
catch {
    Write-Output "[error] week5-e2e failed: $($_.Exception.Message)"
    if ($null -ne $_.ScriptStackTrace) {
        Write-Output "[error] stack: $($_.ScriptStackTrace)"
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

    if ($null -ne $serviceProcess -and -not $serviceProcess.HasExited) {
        Stop-Process -Id $serviceProcess.Id -Force
    }

    if (-not $KeepRunning) {
        Remove-ContainerIfExists -Name $backendContainer
        Remove-ContainerIfExists -Name $serviceContainer
        Remove-ContainerIfExists -Name $postgresContainer
    }
}
