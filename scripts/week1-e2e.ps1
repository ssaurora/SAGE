param(
    [switch]$KeepRunning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendDir = Join-Path $root "BackEnd"
$pass1Dir = Join-Path $root "Service/planning-pass1"

$postgresContainer = "sage-week2-postgres"
$pass1Container = "sage-week2-pass1"

$backendPort = 18080
$pass1Port = 18001
$postgresPort = 15432

$runId = Get-Date -Format "yyyyMMddHHmmss"
$backendLogOut = Join-Path $PSScriptRoot "week2-backend.stdout.$runId.log"
$backendLogErr = Join-Path $PSScriptRoot "week2-backend.stderr.$runId.log"

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
    Write-Output "[1/6] Preparing containers..."
    Remove-ContainerIfExists -Name $postgresContainer
    Remove-ContainerIfExists -Name $pass1Container

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

    cmd /c "docker image inspect sage-pass1:week2 >nul 2>nul"
    if ($LASTEXITCODE -ne 0) {
        Write-Output "Pass1 image not found. Building..."
        Push-Location $pass1Dir
        try {
            docker build -t sage-pass1:week2 . | Out-Host
        } finally {
            Pop-Location
        }
    }

    docker run -d --name $pass1Container -p "${pass1Port}:8001" sage-pass1:week2 | Out-Null

    Wait-Until -TimeoutSeconds 60 -ErrorMessage "Pass1 startup timed out" -Probe {
        $health = Invoke-RestMethod -Method Get -Uri "http://localhost:${pass1Port}/health" -TimeoutSec 2
        return $health.status -eq "ok"
    }

    Write-Output "[2/6] Starting BackEnd..."
    Stop-ProcessListeningOnPort -Port $backendPort

    $env:DB_URL = "jdbc:postgresql://localhost:${postgresPort}/sage"
    $env:DB_USERNAME = "postgres"
    $env:DB_PASSWORD = "postgres"
    $env:PASS1_BASE_URL = "http://localhost:${pass1Port}"
    $env:SERVER_PORT = "${backendPort}"
    $env:JWT_SECRET = "sage-week1-e2e-secret-key-123456789"

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

    Write-Output "[3/6] Verifying auth flow..."
    $loginBody = @{ username = "demo"; password = "demo123" } | ConvertTo-Json
    $loginResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/auth/login" -ContentType "application/json" -Body $loginBody
    Assert-True ($null -ne $loginResponse.access_token -and $loginResponse.access_token.Length -gt 10) "login must return access_token"

    $headers = @{ Authorization = "Bearer $($loginResponse.access_token)" }
    $me = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/auth/me" -Headers $headers
    Assert-True ($me.username -eq "demo") "auth/me must return demo user"

    Write-Output "[4/6] Verifying task creation flow..."
    $createBody = @{ user_query = "Please run a water yield analysis" } | ConvertTo-Json
    $createTask = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks" -Headers $headers -ContentType "application/json" -Body $createBody

    $taskId = [string]$createTask.task_id
    Assert-True (-not [string]::IsNullOrWhiteSpace($taskId)) "create task must return task_id"
    Assert-True ($createTask.state -eq "PLANNING") "create task state should be PLANNING"
    Assert-True ([int]$createTask.state_version -ge 4) "create task state_version should be >= 4"

    Write-Output "[5/6] Verifying task detail and events..."
    $taskDetail = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$taskId" -Headers $headers
    $events = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$taskId/events" -Headers $headers

    Assert-True ($taskDetail.state -eq "PLANNING") "task detail state should be PLANNING"
    Assert-True ($taskDetail.pass1_summary.selected_template -eq "water_yield_v1") "selected_template should be water_yield_v1"
    Assert-True ($null -ne $taskDetail.slot_bindings_summary) "slot_bindings_summary should exist"
    Assert-True ($null -ne $taskDetail.args_draft_summary) "args_draft_summary should exist"
    Assert-True ($null -ne $taskDetail.validation_summary) "validation_summary should exist"

    $isValid = [bool]$taskDetail.validation_summary.is_valid
    $expectedInputChainStatus = if ($isValid) { "COMPLETE" } else { "INCOMPLETE" }
    Assert-True ($taskDetail.input_chain_status -eq $expectedInputChainStatus) "input_chain_status must match validation_summary.is_valid"

    $eventTypes = @($events.items | ForEach-Object { $_.event_type })
    $requiredEvents = @(
        "TASK_CREATED",
        "STATE_CHANGED",
        "PLANNING_PASS1_STARTED",
        "PLANNING_PASS1_COMPLETED",
        "COGNITION_PASSB_STARTED",
        "COGNITION_PASSB_COMPLETED",
        "VALIDATION_STARTED"
    )
    foreach ($eventType in $requiredEvents) {
        Assert-True ($eventTypes -contains $eventType) "missing event: $eventType"
    }
    Assert-True (($eventTypes -contains "VALIDATION_PASSED") -or ($eventTypes -contains "VALIDATION_FAILED")) "missing validation result event"

    Write-Output "[6/6] E2E passed"
    Write-Output "task_id: $taskId"
    Write-Output "state: $($taskDetail.state), state_version: $($taskDetail.state_version)"
    Write-Output "input_chain_status: $($taskDetail.input_chain_status)"
    Write-Output "events: $($eventTypes -join ', ')"

    if ($KeepRunning) {
        Write-Output "KeepRunning=true, keeping BackEnd process and containers alive."
        Write-Output "BackEnd: http://localhost:${backendPort}"
        Write-Output "Pass1: http://localhost:${pass1Port}"
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
        Remove-ContainerIfExists -Name $pass1Container
        Remove-ContainerIfExists -Name $postgresContainer
    }
}
