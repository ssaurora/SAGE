param(
    [switch]$KeepRunning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendDir = Join-Path $root "BackEnd"
$serviceDir = Join-Path $root "Service/planning-pass1"

$postgresContainer = "sage-week3-postgres"
$serviceContainer = "sage-week3-service"

$backendPort = 18080
$servicePort = 18001
$postgresPort = 15432

$runId = Get-Date -Format "yyyyMMddHHmmss"
$backendLogOut = Join-Path $PSScriptRoot "week3-backend.stdout.$runId.log"
$backendLogErr = Join-Path $PSScriptRoot "week3-backend.stderr.$runId.log"

$backendProcess = $null

function Wait-Until {
    param(
        [scriptblock]$Probe,
        [int]$TimeoutSeconds = 120,
        [string]$ErrorMessage = "Timeout"
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            if (& $Probe) {
                return
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }
    throw $ErrorMessage
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

    cmd /c "docker container inspect $Name >nul 2>nul"
    if ($LASTEXITCODE -eq 0) {
        cmd /c "docker rm -f $Name >nul 2>nul"
    }
}

function Stop-ProcessListeningOnPort {
    param([int]$Port)

    $listeners = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique

    foreach ($processId in $listeners) {
        if ($processId -and $processId -ne 0) {
            try {
                Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
            } catch {
            }
        }
    }
}

$envBackup = @{}
$envVars = @(
    "DB_URL",
    "DB_USERNAME",
    "DB_PASSWORD",
    "PASS1_BASE_URL",
    "SERVER_PORT",
    "JWT_SECRET"
)

foreach ($name in $envVars) {
    $envBackup[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

try {
    Write-Output "[1/7] Preparing containers..."
    Remove-ContainerIfExists -Name $postgresContainer
    Remove-ContainerIfExists -Name $serviceContainer

    docker run -d --name $postgresContainer `
        -e POSTGRES_DB=sage `
        -e POSTGRES_USER=postgres `
        -e POSTGRES_PASSWORD=postgres `
        -p "${postgresPort}:5432" `
        postgres:16-alpine | Out-Null

    Wait-Until -TimeoutSeconds 90 -ErrorMessage "PostgreSQL startup timed out" -Probe {
        docker exec $postgresContainer pg_isready -U postgres -d sage 2>$null | Out-Null
        return $LASTEXITCODE -eq 0
    }

    cmd /c "docker image inspect sage-pass1:week3 >nul 2>nul"
    if ($LASTEXITCODE -ne 0) {
        Write-Output "Service image not found. Building..."
        Push-Location $serviceDir
        try {
            docker build -t sage-pass1:week3 . | Out-Host
        } finally {
            Pop-Location
        }
    }

    docker run -d --name $serviceContainer -p "${servicePort}:8001" sage-pass1:week3 | Out-Null

    Wait-Until -TimeoutSeconds 60 -ErrorMessage "Service startup timed out" -Probe {
        $health = Invoke-RestMethod -Method Get -Uri "http://localhost:${servicePort}/health" -TimeoutSec 2
        return $health.status -eq "ok"
    }

    Write-Output "[2/7] Starting BackEnd..."
    Stop-ProcessListeningOnPort -Port $backendPort

    $env:DB_URL = "jdbc:postgresql://localhost:${postgresPort}/sage"
    $env:DB_USERNAME = "postgres"
    $env:DB_PASSWORD = "postgres"
    $env:PASS1_BASE_URL = "http://localhost:${servicePort}"
    $env:SERVER_PORT = "${backendPort}"
    $env:JWT_SECRET = "sage-week3-e2e-secret-key-123456789"

    $backendProcess = Start-Process -FilePath "mvn" `
        -ArgumentList "spring-boot:run", "-q" `
        -WorkingDirectory $backendDir `
        -RedirectStandardOutput $backendLogOut `
        -RedirectStandardError $backendLogErr `
        -PassThru

    Wait-Until -TimeoutSeconds 150 -ErrorMessage "BackEnd startup timed out" -Probe {
        if ($backendProcess.HasExited) {
            throw "BackEnd exited early with code $($backendProcess.ExitCode)"
        }
        $health = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/actuator/health" -TimeoutSec 2
        return $health.status -eq "UP"
    }

    Write-Output "[3/7] Verifying auth flow..."
    $loginBody = @{ username = "demo"; password = "demo123" } | ConvertTo-Json
    $loginResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/auth/login" -ContentType "application/json" -Body $loginBody
    Assert-True ($null -ne $loginResponse.access_token -and $loginResponse.access_token.Length -gt 10) "login must return access_token"

    $headers = @{ Authorization = "Bearer $($loginResponse.access_token)" }
    $me = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/auth/me" -Headers $headers
    Assert-True ($me.username -eq "demo") "auth/me must return demo user"

    Write-Output "[4/7] Creating task..."
    $createBody = @{ user_query = "Please run week3 execution chain" } | ConvertTo-Json
    $createTask = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks" -Headers $headers -ContentType "application/json" -Body $createBody
    $taskId = [string]$createTask.task_id

    Assert-True (-not [string]::IsNullOrWhiteSpace($taskId)) "create task must return task_id"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$createTask.job_id)) "create task should return job_id on valid chain"
    Assert-True ($createTask.state -eq "QUEUED") "valid chain create task state should be QUEUED"

    Write-Output "[5/7] Waiting for job terminal state..."
    $terminalReached = $false
    $taskDetail = $null
    for ($i = 0; $i -lt 30; $i++) {
        $taskDetail = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$taskId" -Headers $headers
        if ($taskDetail.state -eq "SUCCEEDED" -or $taskDetail.state -eq "FAILED") {
            $terminalReached = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    Assert-True $terminalReached "task should reach terminal state"

    Write-Output "[6/7] Verifying detail and event timeline..."
    $events = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$taskId/events" -Headers $headers

    Assert-True ($null -ne $taskDetail.job) "task detail should include job block"
    Assert-True ($taskDetail.job.job_id -eq $createTask.job_id) "job mapping should match"
    Assert-True ($null -ne $taskDetail.pass2_summary) "pass2_summary should exist"

    if ($taskDetail.state -eq "SUCCEEDED") {
        Assert-True ($null -ne $taskDetail.result_object_summary) "success task should include result_object_summary"
    }

    $eventTypes = @($events.items | ForEach-Object { $_.event_type })
    $requiredEvents = @(
        "PLANNING_PASS2_STARTED",
        "PLANNING_PASS2_COMPLETED",
        "JOB_SUBMITTED",
        "JOB_STATE_CHANGED"
    )
    foreach ($eventType in $requiredEvents) {
        Assert-True ($eventTypes -contains $eventType) "missing event: $eventType"
    }
    Assert-True (($eventTypes -contains "RESULT_OBJECT_READY") -or ($taskDetail.state -eq "FAILED")) "missing RESULT_OBJECT_READY for success path"

    Write-Output "[7/7] Week3 E2E passed"
    Write-Output "task_id: $taskId"
    Write-Output "job_id: $($createTask.job_id)"
    Write-Output "state: $($taskDetail.state), state_version: $($taskDetail.state_version)"
    Write-Output "events: $($eventTypes -join ', ')"

    if ($KeepRunning) {
        Write-Output "KeepRunning=true, keeping BackEnd process and containers alive."
        Write-Output "BackEnd: http://localhost:${backendPort}"
        Write-Output "Service: http://localhost:${servicePort}"
        Write-Output "PostgreSQL: localhost:${postgresPort}"
        $backendProcess = $null
    }
}
finally {
    foreach ($name in $envVars) {
        [Environment]::SetEnvironmentVariable($name, $envBackup[$name], "Process")
    }

    if ($null -ne $backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id -Force
    }

    if (-not $KeepRunning) {
        Remove-ContainerIfExists -Name $serviceContainer
        Remove-ContainerIfExists -Name $postgresContainer
    }
}

