param(
    [switch]$KeepRunning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendDir = Join-Path $root "BackEnd"
$serviceDir = Join-Path $root "Service/planning-pass1"

$postgresContainer = "sage-week4-postgres"
$serviceContainer = "sage-week4-service"

$backendPort = 28080
$servicePort = 28001
$postgresPort = 25432

$runId = Get-Date -Format "yyyyMMddHHmmss"
$backendLogOut = Join-Path $PSScriptRoot "week4-backend.stdout.$runId.log"
$backendLogErr = Join-Path $PSScriptRoot "week4-backend.stderr.$runId.log"

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
    $containerId = (docker ps -aq --filter "name=^$Name$")
    if ($containerId) {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        docker rm -f $Name | Out-Null
        $ErrorActionPreference = $previousPreference
    }
}

function Stop-ProcessListeningOnPort {
    param([int]$Port)
    $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($listeners) {
        $pids = $listeners | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($owningPid in $pids) {
            try {
                Stop-Process -Id $owningPid -Force -ErrorAction Stop
            } catch {
            }
        }
    }
}

$envVars = @("DB_URL", "DB_USERNAME", "DB_PASSWORD", "PASS1_BASE_URL", "SERVER_PORT", "JWT_SECRET")
$envBackup = @{}
foreach ($name in $envVars) {
    $envBackup[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

try {
    Write-Output "[1/8] Starting PostgreSQL container..."
    Remove-ContainerIfExists -Name $postgresContainer
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $pgRun = docker run --name $postgresContainer --rm -d -p ${postgresPort}:5432 `
        -e POSTGRES_DB=sage `
        -e POSTGRES_USER=postgres `
        -e POSTGRES_PASSWORD=postgres `
        postgres:17 2>&1
    $ErrorActionPreference = $previousPreference
    if ($LASTEXITCODE -ne 0) {
        throw "failed to start postgres container: $pgRun"
    }

    Wait-Until -TimeoutSeconds 90 -ErrorMessage "PostgreSQL startup timed out" -Probe {
        $probe = docker exec $postgresContainer pg_isready -U postgres -d sage
        return ($LASTEXITCODE -eq 0)
    }

    Write-Output "[2/8] Starting Service container..."
    Remove-ContainerIfExists -Name $serviceContainer
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $buildOutput = docker build -t sage-pass1:week4 $serviceDir 2>&1
    $buildExit = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    if ($buildExit -ne 0) {
        throw "failed to build service image: $buildOutput"
    }
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $serviceRun = docker run --name $serviceContainer --rm -d -p ${servicePort}:8001 sage-pass1:week4 2>&1
    $serviceRunExit = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    if ($serviceRunExit -ne 0) {
        throw "failed to start service container: $serviceRun"
    }

    Wait-Until -TimeoutSeconds 90 -ErrorMessage "Service startup timed out" -Probe {
        try {
            $health = Invoke-RestMethod -Method Get -Uri "http://localhost:${servicePort}/health" -TimeoutSec 2
            return $health.status -eq "ok"
        } catch {
            return $false
        }
    }

    Write-Output "[3/8] Starting BackEnd..."
    Stop-ProcessListeningOnPort -Port $backendPort

    $env:DB_URL = "jdbc:postgresql://localhost:${postgresPort}/sage"
    $env:DB_USERNAME = "postgres"
    $env:DB_PASSWORD = "postgres"
    $env:PASS1_BASE_URL = "http://localhost:${servicePort}"
    $env:SERVER_PORT = "${backendPort}"
    $env:JWT_SECRET = "sage-week4-e2e-secret-key-123456789"

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

    Write-Output "[4/8] Login..."
    $loginBody = @{ username = "demo"; password = "demo123" } | ConvertTo-Json
    $loginResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/auth/login" -ContentType "application/json" -Body $loginBody
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$loginResponse.access_token)) "login must return access token"
    $headers = @{ Authorization = "Bearer $($loginResponse.access_token)" }

    Write-Output "[5/8] Success path verification..."
    $task1Body = @{ user_query = "Run week4 success chain" } | ConvertTo-Json
    $task1 = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks" -Headers $headers -ContentType "application/json" -Body $task1Body
    $task1Id = [string]$task1.task_id
    Assert-True (-not [string]::IsNullOrWhiteSpace($task1Id)) "task1 id should exist"

    $task1Final = $null
    for ($i = 0; $i -lt 50; $i++) {
        $task1Final = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$task1Id" -Headers $headers
        if ($task1Final.state -eq "SUCCEEDED" -or $task1Final.state -eq "FAILED" -or $task1Final.state -eq "CANCELLED") {
            break
        }
        Start-Sleep -Seconds 2
    }
    Assert-True ($task1Final.state -eq "SUCCEEDED") "task1 should reach SUCCEEDED"

    $task1Result = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$task1Id/result" -Headers $headers
    Assert-True ($null -ne $task1Result.result_bundle) "result_bundle should exist for success"
    Assert-True ($null -ne $task1Result.final_explanation) "final_explanation should exist for success"
    Assert-True ($null -ne $task1Result.docker_runtime_evidence) "docker_runtime_evidence should exist"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$task1Result.docker_runtime_evidence.image)) "docker evidence image should exist"

    Write-Output "[6/8] Cancel path verification..."
    $task2Body = @{ user_query = "Run week4 cancel chain" } | ConvertTo-Json
    $task2 = Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks" -Headers $headers -ContentType "application/json" -Body $task2Body
    $task2Id = [string]$task2.task_id
    Assert-True (-not [string]::IsNullOrWhiteSpace($task2Id)) "task2 id should exist"

    Start-Sleep -Milliseconds 300
    $cancelStatus = 0
    try {
        Invoke-RestMethod -Method Post -Uri "http://localhost:${backendPort}/tasks/$task2Id/cancel" -Headers $headers -ContentType "application/json" -Body "{}" | Out-Null
        $cancelStatus = 202
    } catch {
        $response = $_.Exception.Response
        if ($null -ne $response -and [int]$response.StatusCode -eq 409) {
            $cancelStatus = 409
        } else {
            throw
        }
    }
    Assert-True ($cancelStatus -eq 202 -or $cancelStatus -eq 409) "cancel should return 202 or 409"

    $task2Final = $null
    for ($i = 0; $i -lt 40; $i++) {
        $task2Final = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$task2Id" -Headers $headers
        if ($task2Final.state -eq "CANCELLED" -or $task2Final.state -eq "SUCCEEDED" -or $task2Final.state -eq "FAILED") {
            break
        }
        Start-Sleep -Seconds 2
    }
    Assert-True ($task2Final.state -eq "CANCELLED" -or $task2Final.state -eq "SUCCEEDED" -or $task2Final.state -eq "FAILED") "task2 should be terminal"

    $task2Result = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$task2Id/result" -Headers $headers
    if ($task2Final.state -eq "CANCELLED" -or $task2Final.state -eq "FAILED") {
        Assert-True ($null -ne $task2Result.failure_summary) "failure_summary should exist for cancelled/failed task"
    }

    Write-Output "[7/8] Event ordering spot check..."
    $events2 = Invoke-RestMethod -Method Get -Uri "http://localhost:${backendPort}/tasks/$task2Id/events" -Headers $headers
    $eventTypes2 = @($events2.items | ForEach-Object { $_.event_type })
    if ($task2Final.state -eq "CANCELLED") {
        Assert-True ($eventTypes2 -contains "CANCEL_REQUESTED") "cancelled task should contain CANCEL_REQUESTED"
        Assert-True ($eventTypes2 -contains "JOB_CANCELLED") "cancelled task should contain JOB_CANCELLED"
        Assert-True ($eventTypes2 -contains "TASK_CANCELLED") "cancelled task should contain TASK_CANCELLED"
    }

    Write-Output "[8/8] Week4 E2E passed"
    Write-Output "task1: $task1Id => $($task1Final.state)"
    Write-Output "task2: $task2Id => $($task2Final.state)"

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
