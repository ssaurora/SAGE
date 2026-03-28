param(
    [string]$SampleDataRoot,
    [string]$InvestPipSpec = "natcap.invest",
    [switch]$KeepRunning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$composeUpScript = Join-Path $PSScriptRoot "compose-up.ps1"
$defaultCaseDir = Join-Path $root "sample data\\Annual_Water_Yield"

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw "Assertion failed: $Message"
    }
}

function Wait-Until {
    param(
        [scriptblock]$Probe,
        [int]$TimeoutSeconds = 180,
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
        Start-Sleep -Seconds 3
    }
    if ($null -ne $lastError) {
        throw "$ErrorMessage`nLast probe error: $($lastError.Exception.Message)"
    }
    throw $ErrorMessage
}

function Test-HttpOk {
    param([string]$Url)
    try {
        $response = Invoke-RestMethod -Method Get -Uri $Url
        return $response.status -eq "ok" -or $response.status -eq "UP"
    } catch {
        return $false
    }
}

$requiredCaseFiles = @(
    "annual_water_yield_gura.invs.json",
    "biophysical_table_gura.csv",
    "depth_to_root_restricting_layer_gura.tif",
    "land_use_gura.tif",
    "plant_available_water_fraction_gura.tif",
    "precipitation_gura.tif",
    "reference_ET_gura.tif",
    "subwatersheds_gura.shp",
    "subwatersheds_gura.dbf",
    "subwatersheds_gura.shx",
    "subwatersheds_gura.prj",
    "watershed_gura.shp",
    "watershed_gura.dbf",
    "watershed_gura.shx",
    "watershed_gura.prj"
)

if ([string]::IsNullOrWhiteSpace($SampleDataRoot)) {
    $SampleDataRoot = $defaultCaseDir
}

$resolvedInputPath = (Resolve-Path $SampleDataRoot).Path
$caseDir = $null
$hostSampleRoot = $null
if (Test-Path (Join-Path $resolvedInputPath "annual_water_yield_gura.invs.json")) {
    $caseDir = $resolvedInputPath
    $hostSampleRoot = Split-Path -Parent $resolvedInputPath
}
elseif (Test-Path (Join-Path $resolvedInputPath "Annual_Water_Yield\\annual_water_yield_gura.invs.json")) {
    $hostSampleRoot = $resolvedInputPath
    $caseDir = Join-Path $resolvedInputPath "Annual_Water_Yield"
}
else {
    throw "SampleDataRoot must point to the Annual_Water_Yield case folder or its parent sample-data root. Input: $resolvedInputPath"
}

foreach ($relativePath in $requiredCaseFiles) {
    $fullPath = Join-Path $caseDir $relativePath
    Assert-True (Test-Path $fullPath) "required sample data path is missing: $fullPath"
}

$previousSampleRoot = $env:SAGE_SAMPLE_DATA_ROOT_HOST
$previousContainerSampleRoot = $env:SAGE_SAMPLE_DATA_ROOT
$previousInvestSpec = $env:SAGE_INVEST_PIP_SPEC
$previousFrontendPort = $env:SAGE_FRONTEND_PORT

try {
    $env:SAGE_SAMPLE_DATA_ROOT_HOST = $hostSampleRoot
    $env:SAGE_SAMPLE_DATA_ROOT = "/sample-data"
    $env:SAGE_INVEST_PIP_SPEC = $InvestPipSpec
    if ([string]::IsNullOrWhiteSpace($env:SAGE_FRONTEND_PORT)) {
        $env:SAGE_FRONTEND_PORT = "3301"
    }

    & $composeUpScript -Build

    Wait-Until -TimeoutSeconds 240 -ErrorMessage "service health did not become ready" -Probe {
        (Test-HttpOk "http://localhost:8001/health") -and (Test-HttpOk "http://localhost:8080/actuator/health")
    }

    $loginBody = @{ username = "demo"; password = "demo123" } | ConvertTo-Json
    $loginResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/auth/login" -ContentType "application/json" -Body $loginBody
    $token = [string]$loginResponse.access_token
    Assert-True (-not [string]::IsNullOrWhiteSpace($token)) "login token should exist"
    $headers = @{ Authorization = "Bearer $token" }

    $createBody = @{ user_query = "run a real case invest annual water yield analysis for gura" } | ConvertTo-Json
    $task = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/tasks" -Headers $headers -ContentType "application/json" -Body $createBody
    $taskId = [string]$task.task_id
    Assert-True (-not [string]::IsNullOrWhiteSpace($taskId)) "task_id should exist"

    $detail = $null
    Wait-Until -TimeoutSeconds 300 -ErrorMessage "task did not reach a terminal or waiting state" -Probe {
        $script:detail = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/tasks/$taskId" -Headers $headers
        $script:detail.state -in @("SUCCEEDED", "FAILED", "WAITING_USER", "STATE_CORRUPTED")
    }

    Assert-True ([string]$detail.skill_route_summary.execution_mode -eq "real_case_validation") "skill_route_summary.execution_mode should be real_case_validation"
    Assert-True ([string]$detail.skill_route_summary.provider_preference -eq "planning-pass1-invest-local") "provider_preference should target the invest provider"

    if ($detail.job) {
        Assert-True ([string]$detail.job.provider_key -eq "planning-pass1-invest-local") "job.provider_key should be planning-pass1-invest-local"
        Assert-True ([string]$detail.job.runtime_profile -eq "docker-invest-real") "job.runtime_profile should be docker-invest-real"
        Assert-True ([string]$detail.job.case_id -eq "annual_water_yield_gura") "job.case_id should be annual_water_yield_gura"
    }

    $result = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/tasks/$taskId/result" -Headers $headers
    if ($detail.state -eq "SUCCEEDED") {
        Assert-True ([string]$result.provider_key -eq "planning-pass1-invest-local") "result.provider_key should be planning-pass1-invest-local"
        Assert-True ([string]$result.runtime_profile -eq "docker-invest-real") "result.runtime_profile should be docker-invest-real"
        Assert-True ([string]$result.case_id -eq "annual_water_yield_gura") "result.case_id should be annual_water_yield_gura"
        Assert-True ([string]$result.docker_runtime_evidence.runtime_mode -eq "invest_real_runner") "runtime_mode should be invest_real_runner"
        Assert-True (@($result.docker_runtime_evidence.input_bindings).Count -ge 5) "input_bindings should include real-case contract inputs"
        Assert-True ($null -ne $result.result_bundle.output_registry) "result_bundle.output_registry should exist"
        Assert-True (@($result.result_bundle.primary_output_refs).Count -ge 1) "primary_output_refs should exist"
        Write-Output "Phase3 real-case E2E passed: $taskId"
    } else {
        Write-Output "Phase3 real-case E2E stopped in state $($detail.state): $taskId"
        Write-Output ($result | ConvertTo-Json -Depth 8)
        throw "real-case provider did not reach SUCCEEDED"
    }
}
finally {
    if (-not $KeepRunning) {
        Push-Location $root
        try {
            docker compose --env-file .env.compose down
        } finally {
            Pop-Location
        }
    }
    $env:SAGE_SAMPLE_DATA_ROOT_HOST = $previousSampleRoot
    $env:SAGE_SAMPLE_DATA_ROOT = $previousContainerSampleRoot
    $env:SAGE_INVEST_PIP_SPEC = $previousInvestSpec
    $env:SAGE_FRONTEND_PORT = $previousFrontendPort
}
