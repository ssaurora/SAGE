param(
    [switch]$Build
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendDir = Join-Path $root "BackEnd"
$frontendDir = Join-Path $root "FrontEnd"
$serviceDir = Join-Path $root "Service/planning-pass1"
$postgresContainer = "sage-postgres"
$redisContainer = "sage-redis"
$backendContainer = "sage-backend"
$frontendContainer = "sage-frontend"
$serviceContainer = "sage-service"
$condaExe = "E:/ProgramData/anaconda3/Scripts/conda.exe"
$condaEnvPath = "E:\Anaconda_envs\envs\InVEST"
$fallbackPostgresPort = 25432
$fallbackRedisPort = 26379

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
        }
        catch {
        }
        Start-Sleep -Seconds 2
    }

    throw $ErrorMessage
}

function Remove-ContainerIfExists {
    param([string]$Name)

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker container inspect $Name 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            & docker rm -f $Name | Out-Null
        }
    }
    finally {
        $ErrorActionPreference = $previousPreference
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
            }
            catch {
            }
        }
    }
}

function Test-DockerPullFailure {
    param([string]$Message)

    if ([string]::IsNullOrWhiteSpace($Message)) {
        return $false
    }

    $patterns = @(
        "failed to fetch anonymous token",
        "auth\\.docker\\.io/token",
        "dial tcp",
        "connectex",
        "i/o timeout",
        "TLS handshake timeout",
        "context deadline exceeded"
    )

    foreach ($pattern in $patterns) {
        if ($Message -match $pattern) {
            return $true
        }
    }

    return $false
}

function Invoke-DockerComposeUp {
    param([switch]$Build)

    $args = @("--env-file", ".env.compose", "up", "-d")
    if ($Build) {
        $args += "--build"
    }

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & docker compose @args 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = ($output -join [Environment]::NewLine)
    }
}

function Start-LocalService {
    Write-Output "[fallback] Starting service on host"
    Remove-ContainerIfExists -Name $serviceContainer
    Stop-ProcessListeningOnPort -Port 8001
    New-Item -ItemType Directory -Force -Path (Join-Path $serviceDir "runtime/workspaces") | Out-Null

    $env:REDIS_ENABLED = "true"
    $env:REDIS_URL = "redis://localhost:$fallbackRedisPort/0"
    $env:WORKSPACE_ROOT = Join-Path $serviceDir "runtime/workspaces"
    $env:SAGE_SAMPLE_DATA_ROOT = Join-Path $root "sample data"
    $env:SAGE_COGNITION_PROVIDER = "deterministic"
    $env:SAGE_COGNITION_GOAL_ROUTE_PROVIDER = "deterministic"
    $env:SAGE_COGNITION_PASSB_PROVIDER = "deterministic"
    $env:SAGE_REPAIR_PROVIDER = "deterministic"
    $env:SAGE_FINAL_EXPLANATION_PROVIDER = "deterministic"
    $env:SAGE_REAL_CASE_LLM_REQUIRED = "false"

    $serviceProcess = Start-Process -FilePath $condaExe `
        -ArgumentList @("run", "-p", $condaEnvPath, "--no-capture-output", "python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001") `
        -WorkingDirectory $serviceDir `
        -PassThru

    Wait-Until -TimeoutSeconds 90 -ErrorMessage "Service startup timed out" -Probe {
        try {
            $health = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:8001/health" -TimeoutSec 2
            return $health.status -eq "ok"
        }
        catch {
            return $false
        }
    }

    return $serviceProcess
}

function Start-LocalBackend {
    Write-Output "[fallback] Starting backend on host"
    Remove-ContainerIfExists -Name $backendContainer
    Stop-ProcessListeningOnPort -Port 8080
    New-Item -ItemType Directory -Force -Path (Join-Path $backendDir "runtime/uploads") | Out-Null

    $env:DB_URL = "jdbc:postgresql://localhost:$fallbackPostgresPort/sage"
    $env:DB_USERNAME = "postgres"
    $env:DB_PASSWORD = "postgres"
    $env:PASS1_BASE_URL = "http://localhost:8001"
    $env:SERVER_PORT = "8080"
    $env:JWT_SECRET = "sage-compose-local-secret-key-123456789"
    $env:SAGE_UPLOAD_ROOT = Join-Path $backendDir "runtime/uploads"

    $backendJar = Join-Path $backendDir "target/backend-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path $backendJar)) {
        throw "Backend jar not found: $backendJar"
    }

    $backendProcess = Start-Process -FilePath "java" `
        -ArgumentList @("-jar", $backendJar) `
        -WorkingDirectory $backendDir `
        -PassThru

    Wait-Until -TimeoutSeconds 150 -ErrorMessage "BackEnd startup timed out" -Probe {
        try {
            $health = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:8080/actuator/health" -TimeoutSec 2
            return $health.status -eq "UP"
        }
        catch {
            return $false
        }
    }

    return $backendProcess
}

function Start-LocalFrontend {
    Write-Output "[fallback] Starting frontend on host"
    Remove-ContainerIfExists -Name $frontendContainer
    Stop-ProcessListeningOnPort -Port 3000

    $frontendEnvBackup = @{}
    foreach ($name in @("NEXT_PUBLIC_API_BASE_URL", "PORT")) {
        $frontendEnvBackup[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
    }

    try {
        $env:NEXT_PUBLIC_API_BASE_URL = "http://localhost:8080"
        $env:PORT = "3000"

        Push-Location $frontendDir
        try {
            & npm ci --registry https://registry.npmmirror.com
            if ($LASTEXITCODE -ne 0) {
                throw "npm ci failed"
            }

            & npm run build
            if ($LASTEXITCODE -ne 0) {
                throw "npm run build failed"
            }
        }
        finally {
            Pop-Location
        }

        $frontendProcess = Start-Process -FilePath "npm" `
            -ArgumentList @("run", "start") `
            -WorkingDirectory $frontendDir `
            -PassThru

        Wait-Until -TimeoutSeconds 90 -ErrorMessage "FrontEnd startup timed out" -Probe {
            try {
                $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:3000/login" -TimeoutSec 2
                return $response.StatusCode -ge 200 -and $response.StatusCode -lt 400
            }
            catch {
                return $false
            }
        }

        return $frontendProcess
    }
    finally {
        foreach ($name in $frontendEnvBackup.Keys) {
            [Environment]::SetEnvironmentVariable($name, $frontendEnvBackup[$name], "Process")
        }
    }
}

function Start-LocalFallbackStack {
    Write-Output "[fallback] Docker Hub pulls failed; starting host processes instead"

    try {
        & docker compose --env-file .env.compose down --remove-orphans | Out-Null
    }
    catch {
    }

    Remove-ContainerIfExists -Name $postgresContainer
    Remove-ContainerIfExists -Name $redisContainer

    Write-Output "[fallback] Start PostgreSQL"
    & docker run --name $postgresContainer --rm -d -p ${fallbackPostgresPort}:5432 -e POSTGRES_DB=sage -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:17 | Out-Null
    Wait-Until -TimeoutSeconds 90 -ErrorMessage "PostgreSQL startup timed out" -Probe {
        & docker exec $postgresContainer pg_isready -U postgres -d sage | Out-Null
        return ($LASTEXITCODE -eq 0)
    }

    Write-Output "[fallback] Start Redis"
    & docker run --name $redisContainer --rm -d -p ${fallbackRedisPort}:6379 redis:7-alpine | Out-Null
    Wait-Until -TimeoutSeconds 60 -ErrorMessage "Redis startup timed out" -Probe {
        & docker exec $redisContainer redis-cli ping | Out-Null
        return ($LASTEXITCODE -eq 0)
    }

    Start-LocalService | Out-Null
    Start-LocalBackend | Out-Null
    Start-LocalFrontend | Out-Null
}

Push-Location $root
try {
    Write-Output "[1/3] Package backend jar"
    Push-Location $backendDir
    try {
        mvn -q -DskipTests package
    }
    finally {
        Pop-Location
    }

    Write-Output "[2/3] Start compose stack"
    $composeResult = Invoke-DockerComposeUp -Build:$Build
    if ($composeResult.ExitCode -ne 0) {
        if (Test-DockerPullFailure -Message $composeResult.Output) {
            Write-Warning $composeResult.Output
            Start-LocalFallbackStack
        }
        else {
            throw $composeResult.Output
        }
    }

    Write-Output "[3/3] Endpoints"
    Write-Output "FrontEnd: http://localhost:3000/login"
    Write-Output "BackEnd:  http://localhost:8080/actuator/health"
    Write-Output "Service:  http://localhost:8001/health"
}
finally {
    Pop-Location
}
