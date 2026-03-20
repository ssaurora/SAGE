param(
    [switch]$Build
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $root
try {
    Write-Output "[1/3] Package backend jar"
    Push-Location (Join-Path $root "BackEnd")
    try {
        mvn -q -DskipTests package
    }
    finally {
        Pop-Location
    }

    Write-Output "[2/3] Start compose stack"
    $args = @("--env-file", ".env.compose", "up", "-d")
    if ($Build) {
        $args += "--build"
    }
    docker compose @args

    Write-Output "[3/3] Endpoints"
    Write-Output "FrontEnd: http://localhost:3000/login"
    Write-Output "BackEnd:  http://localhost:8080/actuator/health"
    Write-Output "Service:  http://localhost:8001/health"
}
finally {
    Pop-Location
}
