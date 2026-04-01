param(
    [ValidateSet("All", "Success", "RepairResume", "Cancel")]
    [string]$Scenario = "All",
    [string]$SampleDataRoot,
    [string]$InvestPipSpec = "natcap.invest",
    [double]$RealProviderDelaySeconds = 6,
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

function Get-ServiceContainerName {
    if (-not [string]::IsNullOrWhiteSpace($env:SAGE_SERVICE_CONTAINER)) {
        return $env:SAGE_SERVICE_CONTAINER
    }
    return "sage-service"
}

function Get-TaskDetail {
    param(
        [string]$TaskId,
        [hashtable]$Headers
    )
    return Invoke-RestMethod -Method Get -Uri "http://localhost:8080/tasks/$TaskId" -Headers $Headers
}

function Get-TaskResult {
    param(
        [string]$TaskId,
        [hashtable]$Headers
    )
    return Invoke-RestMethod -Method Get -Uri "http://localhost:8080/tasks/$TaskId/result" -Headers $Headers
}

function Get-TaskArtifacts {
    param(
        [string]$TaskId,
        [hashtable]$Headers
    )
    return Invoke-RestMethod -Method Get -Uri "http://localhost:8080/tasks/$TaskId/artifacts" -Headers $Headers
}

function Get-TaskManifest {
    param(
        [string]$TaskId,
        [hashtable]$Headers
    )
    return Invoke-RestMethod -Method Get -Uri "http://localhost:8080/tasks/$TaskId/manifest" -Headers $Headers
}

function Wait-TaskState {
    param(
        [string]$TaskId,
        [hashtable]$Headers,
        [string[]]$States,
        [int]$TimeoutSeconds = 180
    )

    $script:detail = $null
    Wait-Until -TimeoutSeconds $TimeoutSeconds -ErrorMessage "task $TaskId did not reach state [$($States -join ', ')]" -Probe {
        $script:detail = Get-TaskDetail -TaskId $TaskId -Headers $Headers
        $States -contains [string]$script:detail.state
    }
    return $script:detail
}

function Invoke-Upload {
    param(
        [string]$TaskId,
        [string]$AccessToken,
        [string]$LogicalSlot,
        [string]$FilePath
    )

    $raw = curl.exe -s -X POST "http://localhost:8080/tasks/$TaskId/attachments" `
        -H "Authorization: Bearer $AccessToken" `
        -F "file=@$FilePath" `
        -F "logical_slot=$LogicalSlot"
    return $raw | ConvertFrom-Json
}

function Assert-CommonRealCaseFields {
    param(
        $Detail,
        $Result
    )

    Assert-True ([string]$Detail.skill_route_summary.execution_mode -eq "real_case_validation") "execution_mode should be real_case_validation"
    Assert-True ([string]$Detail.skill_route_summary.provider_preference -eq "planning-pass1-invest-local") "provider_preference should target the invest provider"
    Assert-True ([string]$Result.provider_key -eq "planning-pass1-invest-local") "result.provider_key should be planning-pass1-invest-local"
    Assert-True ([string]$Result.runtime_profile -eq "docker-invest-real") "result.runtime_profile should be docker-invest-real"
    Assert-True ([string]$Result.case_id -eq "annual_water_yield_gura") "result.case_id should be annual_water_yield_gura"
    Assert-True ([string]$Result.docker_runtime_evidence.runtime_mode -eq "invest_real_runner") "runtime_mode should be invest_real_runner"
    Assert-True (@($Result.docker_runtime_evidence.input_bindings).Count -ge 5) "input_bindings should include real-case contract inputs"
    Assert-CognitionMetadata -Metadata $Detail.goal_route_cognition -Stage "goal-route"
    Assert-CognitionMetadata -Metadata $Detail.passb_cognition -Stage "passb"
    Assert-CognitionMetadata -Metadata $Result.final_explanation_cognition -Stage "final-explanation"
}

function Assert-ManifestMatchesAuthority {
    param(
        [hashtable]$Headers,
        [string]$TaskId,
        $Result
    )

    $manifest = Get-TaskManifest -TaskId $TaskId -Headers $Headers
    Assert-True ($null -ne $manifest) "task manifest should exist"
    Assert-True ([string]$Result.result_bundle.metrics.provider_key -eq [string]$Result.provider_key) "result bundle metrics provider_key should match authority facts"
    Assert-True ([string]$Result.result_bundle.metrics.runtime_profile -eq [string]$Result.runtime_profile) "result bundle metrics runtime_profile should match authority facts"
    Assert-True ([string]$Result.result_bundle.metrics.case_id -eq [string]$Result.case_id) "result bundle metrics case_id should match authority facts"
    Assert-True (@($manifest.slot_bindings).Count -eq @($Result.docker_runtime_evidence.input_bindings).Count) "manifest slot_bindings and runtime evidence input_bindings should agree"
}

function Assert-CognitionMetadata {
    param(
        $Metadata,
        [string]$Stage
    )

    Assert-True ($null -ne $Metadata) "$Stage cognition metadata should exist"
    Assert-True ([string]$Metadata.provider -eq "glm") "$Stage cognition provider should be glm"
    Assert-True ($Metadata.fallback_used -eq $false) "$Stage cognition fallback_used should be false"
    Assert-True ($Metadata.schema_valid -ne $false) "$Stage cognition schema_valid should not be false"
}

function Invoke-RealCaseSuccessScenario {
    param(
        [hashtable]$Headers,
        [string]$ServiceContainer
    )

    $createBody = @{ user_query = "run a real case invest annual water yield analysis for gura" } | ConvertTo-Json
    $task = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/tasks" -Headers $Headers -ContentType "application/json" -Body $createBody
    $taskId = [string]$task.task_id
    Assert-True (-not [string]::IsNullOrWhiteSpace($taskId)) "success scenario task_id should exist"

    $detail = Wait-TaskState -TaskId $taskId -Headers $Headers -States @("SUCCEEDED", "FAILED", "WAITING_USER", "STATE_CORRUPTED") -TimeoutSeconds 360
    Assert-True ([string]$detail.state -eq "SUCCEEDED") "real-case success scenario should finish in SUCCEEDED"

    $result = Get-TaskResult -TaskId $taskId -Headers $Headers
    Assert-CommonRealCaseFields -Detail $detail -Result $result
    Assert-True ([bool]$result.result_bundle.metrics.used_real_invest) "used_real_invest should be true"
    Assert-True (@($result.result_bundle.primary_output_refs).Count -ge 1) "primary_output_refs should exist"
    Assert-True (@($result.artifact_catalog.primary_outputs).Count -ge 3) "primary outputs should be recorded"
    Assert-True (@($result.artifact_catalog.audit_artifacts).Count -ge 2) "audit artifacts should be recorded"
    Assert-True (@($result.artifact_catalog.logs).Count -ge 1) "log artifacts should be recorded"
    Assert-ManifestMatchesAuthority -Headers $Headers -TaskId $taskId -Result $result
    Write-Output "Phase3 real-case success passed: $taskId"
    return $taskId
}

function Invoke-RealCaseRepairResumeScenario {
    param(
        [hashtable]$Headers,
        [string]$AccessToken,
        [string]$PrecipitationFile,
        [string]$ServiceContainer
    )

    $createBody = @{ user_query = "run a real case invest annual water yield analysis for gura missing precipitation" } | ConvertTo-Json
    $task = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/tasks" -Headers $Headers -ContentType "application/json" -Body $createBody
    $taskId = [string]$task.task_id
    Assert-True (-not [string]::IsNullOrWhiteSpace($taskId)) "repair/resume scenario task_id should exist"

    $waitingDetail = Wait-TaskState -TaskId $taskId -Headers $Headers -States @("WAITING_USER", "FAILED", "SUCCEEDED") -TimeoutSeconds 180
    Assert-True ([string]$waitingDetail.state -eq "WAITING_USER") "repair/resume scenario should first enter WAITING_USER"
    $missingSlots = @($waitingDetail.waiting_context.missing_slots | ForEach-Object { [string]$_.slot_name })
    Assert-True ($missingSlots -contains "precipitation") "missing_slots should include precipitation"
    $requiredActions = @($waitingDetail.waiting_context.required_user_actions | ForEach-Object { [string]$_.key })
    Assert-True ($requiredActions -contains "upload_precipitation") "required_user_actions should include upload_precipitation"
    Assert-True ($waitingDetail.waiting_context.can_resume -eq $false) "can_resume should be false before upload"
    Assert-CognitionMetadata -Metadata $waitingDetail.goal_route_cognition -Stage "goal-route"
    Assert-CognitionMetadata -Metadata $waitingDetail.passb_cognition -Stage "passb"
    Assert-CognitionMetadata -Metadata $waitingDetail.repair_proposal_cognition -Stage "repair-proposal"

    $uploadResponse = Invoke-Upload -TaskId $taskId -AccessToken $AccessToken -LogicalSlot "precipitation" -FilePath $PrecipitationFile
    Assert-True ([string]$uploadResponse.assignment_status -eq "ASSIGNED") "repair upload should be assigned"

    Wait-Until -TimeoutSeconds 120 -ErrorMessage "waiting_context.can_resume did not become true after upload" -Probe {
        $current = Get-TaskDetail -TaskId $taskId -Headers $Headers
        $current.state -eq "WAITING_USER" -and $current.waiting_context.can_resume -eq $true
    }

    $resumeBody = @{ resume_request_id = [guid]::NewGuid().ToString(); user_note = "uploaded real precipitation raster" } | ConvertTo-Json
    $resumeResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/tasks/$taskId/resume" -Headers $Headers -ContentType "application/json" -Body $resumeBody
    Assert-True ($resumeResponse.resume_accepted -eq $true) "repair resume should be accepted"

    $detail = Wait-TaskState -TaskId $taskId -Headers $Headers -States @("SUCCEEDED", "FAILED", "WAITING_USER", "STATE_CORRUPTED") -TimeoutSeconds 360
    Assert-True ([string]$detail.state -eq "SUCCEEDED") "repair/resume scenario should finish in SUCCEEDED"

    $result = Get-TaskResult -TaskId $taskId -Headers $Headers
    Assert-CommonRealCaseFields -Detail $detail -Result $result
    Assert-True ([bool]$result.result_bundle.metrics.used_real_invest) "repair/resume result should still use real InVEST"
    Assert-ManifestMatchesAuthority -Headers $Headers -TaskId $taskId -Result $result
    Write-Output "Phase3 real-case repair/resume passed: $taskId"
    return $taskId
}

function Invoke-RealCaseCancelScenario {
    param(
        [hashtable]$Headers
    )

    $createBody = @{ user_query = "run a real case invest annual water yield analysis for gura" } | ConvertTo-Json
    $task = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/tasks" -Headers $Headers -ContentType "application/json" -Body $createBody
    $taskId = [string]$task.task_id
    Assert-True (-not [string]::IsNullOrWhiteSpace($taskId)) "cancel scenario task_id should exist"

    Wait-Until -TimeoutSeconds 180 -ErrorMessage "cancel scenario did not create a running job in time" -Probe {
        $snapshot = Get-TaskDetail -TaskId $taskId -Headers $Headers
        $snapshot.job -and [string]$snapshot.job.job_id -ne "" -and ([string]$snapshot.job.job_state -in @("RUNNING", "QUEUED"))
    }

    $cancelStatus = 0
    try {
        $cancelResponse = Invoke-WebRequest -Method Post -Uri "http://localhost:8080/tasks/$taskId/cancel" -Headers $Headers -ContentType "application/json" -Body "{}"
        $cancelStatus = [int]$cancelResponse.StatusCode
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $cancelStatus = [int]$_.Exception.Response.StatusCode
        } else {
            throw
        }
    }
    Assert-True ($cancelStatus -eq 202 -or $cancelStatus -eq 409) "cancel should return 202 or 409, got $cancelStatus"

    $detail = Wait-TaskState -TaskId $taskId -Headers $Headers -States @("CANCELLED", "FAILED", "SUCCEEDED") -TimeoutSeconds 240
    Assert-True ([string]$detail.state -ne "SUCCEEDED") "cancel scenario should not finish as SUCCEEDED"

    $result = Get-TaskResult -TaskId $taskId -Headers $Headers
    Assert-True ($null -ne $result.failure_summary) "cancel scenario should retain failure_summary"
    $artifacts = Get-TaskArtifacts -TaskId $taskId -Headers $Headers
    Assert-True (@($artifacts.items).Count -ge 1) "cancel scenario should retain artifact/workspace records"
    $attempt = @($artifacts.items)[0]
    Assert-True (@($attempt.artifacts.logs).Count -ge 1) "cancel scenario should retain log artifacts"
    Write-Output "Phase3 real-case cancel passed: $taskId => $($detail.state)"
    return $taskId
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
$previousServiceDockerfile = $env:SAGE_SERVICE_DOCKERFILE
$previousServiceImage = $env:SAGE_SERVICE_IMAGE
$previousRuntimeImage = $env:SAGE_SERVICE_RUNTIME_IMAGE
$previousDelay = $env:SAGE_INVEST_REAL_PRESTART_DELAY_SECONDS
$previousCognitionProvider = $env:SAGE_COGNITION_PROVIDER
$previousGoalRouteProvider = $env:SAGE_COGNITION_GOAL_ROUTE_PROVIDER
$previousPassbProvider = $env:SAGE_COGNITION_PASSB_PROVIDER
$previousRepairProvider = $env:SAGE_REPAIR_PROVIDER
$previousFinalExplanationProvider = $env:SAGE_FINAL_EXPLANATION_PROVIDER
$previousRealCaseLlmRequired = $env:SAGE_REAL_CASE_LLM_REQUIRED

try {
    $env:SAGE_SAMPLE_DATA_ROOT_HOST = $hostSampleRoot
    $env:SAGE_SAMPLE_DATA_ROOT = "/sample-data"
    $env:SAGE_INVEST_PIP_SPEC = $InvestPipSpec
    $env:SAGE_COGNITION_PROVIDER = "glm"
    $env:SAGE_COGNITION_GOAL_ROUTE_PROVIDER = "glm"
    $env:SAGE_COGNITION_PASSB_PROVIDER = "glm"
    $env:SAGE_REPAIR_PROVIDER = "glm"
    $env:SAGE_FINAL_EXPLANATION_PROVIDER = "glm"
    $env:SAGE_REAL_CASE_LLM_REQUIRED = "true"
    $env:SAGE_SERVICE_DOCKERFILE = "Dockerfile.invest-real"
    $env:SAGE_SERVICE_IMAGE = "sage-pass1-invest-real:compose"
    $env:SAGE_SERVICE_RUNTIME_IMAGE = "sage-pass1-invest-real:compose"
    $env:SAGE_INVEST_REAL_PRESTART_DELAY_SECONDS = [string]$RealProviderDelaySeconds
    if ([string]::IsNullOrWhiteSpace($env:SAGE_FRONTEND_PORT)) {
        $env:SAGE_FRONTEND_PORT = "3301"
    }

    & $composeUpScript -Build

    Wait-Until -TimeoutSeconds 360 -ErrorMessage "service health did not become ready" -Probe {
        (Test-HttpOk "http://localhost:8001/health") -and (Test-HttpOk "http://localhost:8080/actuator/health")
    }

    $loginBody = @{ username = "demo"; password = "demo123" } | ConvertTo-Json
    $loginResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/auth/login" -ContentType "application/json" -Body $loginBody
    $token = [string]$loginResponse.access_token
    Assert-True (-not [string]::IsNullOrWhiteSpace($token)) "login token should exist"
    $headers = @{ Authorization = "Bearer $token" }
    $serviceContainer = Get-ServiceContainerName
    $precipitationFile = Join-Path $caseDir "precipitation_gura.tif"

    $executedTaskIds = New-Object System.Collections.Generic.List[string]

    if ($Scenario -in @("All", "Success")) {
        $executedTaskIds.Add((Invoke-RealCaseSuccessScenario -Headers $headers -ServiceContainer $serviceContainer))
    }
    if ($Scenario -in @("All", "RepairResume")) {
        $executedTaskIds.Add((Invoke-RealCaseRepairResumeScenario -Headers $headers -AccessToken $token -PrecipitationFile $precipitationFile -ServiceContainer $serviceContainer))
    }
    if ($Scenario -in @("All", "Cancel")) {
        $executedTaskIds.Add((Invoke-RealCaseCancelScenario -Headers $headers))
    }

    Write-Output "Phase3 real-case E2E passed for scenario $Scenario"
    Write-Output ("task_ids: " + ($executedTaskIds -join ", "))
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
    $env:SAGE_SERVICE_DOCKERFILE = $previousServiceDockerfile
    $env:SAGE_SERVICE_IMAGE = $previousServiceImage
    $env:SAGE_SERVICE_RUNTIME_IMAGE = $previousRuntimeImage
    $env:SAGE_INVEST_REAL_PRESTART_DELAY_SECONDS = $previousDelay
    $env:SAGE_COGNITION_PROVIDER = $previousCognitionProvider
    $env:SAGE_COGNITION_GOAL_ROUTE_PROVIDER = $previousGoalRouteProvider
    $env:SAGE_COGNITION_PASSB_PROVIDER = $previousPassbProvider
    $env:SAGE_REPAIR_PROVIDER = $previousRepairProvider
    $env:SAGE_FINAL_EXPLANATION_PROVIDER = $previousFinalExplanationProvider
    $env:SAGE_REAL_CASE_LLM_REQUIRED = $previousRealCaseLlmRequired
}
