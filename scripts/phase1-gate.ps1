param(
    [switch]$SkipWeek4,
    [switch]$SkipWeek5,
    [switch]$SkipWeek6,
    [int]$WeekCooldownSeconds = 8
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$gateDefinitions = [ordered]@{
    G1 = "WAITING_USER_ENTRY"
    G2 = "REPAIR_RESUME_FLOW"
    G3 = "SUCCESS_RESULT_BUNDLE"
    G4 = "FAILURE_STRUCTURED"
    G5 = "CANCEL_TERMINATION"
    G6 = "WORKSPACE_CLEANUP"
    G7 = "ARTIFACT_TRACEABILITY"
    G8 = "REDIS_BOUNDARY"
    G9 = "LLM_BOUNDARY"
}

$failDefinitions = [ordered]@{
    F1 = "Resume accepted while can_resume=false"
    F2 = "LLM influences dispatcher authority"
    F3 = "Workspace residue without record"
    F4 = "Artifact without index"
    F5 = "Cancel continues running"
    F6 = "Redis outage corrupts truth"
    F7 = "Java owns prompt/provider/model"
}

$moduleResults = New-Object System.Collections.Generic.List[object]
$gateResults = @{}
$gateReasons = @{}
$triggeredFailConditions = New-Object System.Collections.Generic.List[string]
$infraFailures = New-Object System.Collections.Generic.List[string]

function Add-UniqueItem {
    param(
        [System.Collections.Generic.List[string]]$List,
        [string]$Value
    )
    if (-not $List.Contains($Value)) {
        $List.Add($Value)
    }
}

function Set-GateResult {
    param(
        [string]$GateId,
        [bool]$Passed,
        [string]$Reason = $null
    )

    if ($gateResults.ContainsKey($GateId)) {
        if ($gateResults[$GateId] -eq $false) {
            return
        }
    }
    $gateResults[$GateId] = $Passed
    if (-not $Passed -and $Reason) {
        $gateReasons[$GateId] = $Reason
    }
}

function Invoke-DockerQuiet {
    param(
        [string[]]$Arguments
    )

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & docker @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = ($output -join [Environment]::NewLine)
    }
}

function Test-DockerReady {
    $probe = Invoke-DockerQuiet -Arguments @("info")
    return ($probe.ExitCode -eq 0)
}

function Wait-DockerReady {
    param(
        [int]$TimeoutSeconds = 45,
        [string]$Stage = "docker preflight"
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastOutput = ""
    while ((Get-Date) -lt $deadline) {
        $probe = Invoke-DockerQuiet -Arguments @("info")
        if ($probe.ExitCode -eq 0) {
            return
        }
        $lastOutput = $probe.Output
        Start-Sleep -Seconds 3
    }

    throw "$Stage failed: docker engine not ready. $lastOutput"
}

function Clear-WeekContainers {
    param(
        [string[]]$ContainerNames
    )

    foreach ($name in $ContainerNames) {
        if ([string]::IsNullOrWhiteSpace($name)) {
            continue
        }
        Invoke-DockerQuiet -Arguments @("rm", "-f", $name) | Out-Null
    }
}

function Test-InfraFailure {
    param([string]$Message)

    if ([string]::IsNullOrWhiteSpace($Message)) {
        return $false
    }

    $patterns = @(
        "failed to connect to the docker API",
        "dockerDesktopLinuxEngine",
        "failed to fetch anonymous token",
        "dial tcp",
        "connectex",
        "i/o timeout",
        "TLS handshake timeout",
        "context deadline exceeded",
        "no space left on device"
    )

    foreach ($pattern in $patterns) {
        if ($Message -match [regex]::Escape($pattern)) {
            return $true
        }
    }
    return $false
}

function Invoke-GateScript {
    param(
        [string]$ScriptPath,
        [string]$ModuleName,
        [string[]]$AssertionIds,
        [string[]]$FailConditionIds = @()
    )

    try {
        & powershell.exe -ExecutionPolicy Bypass -File $ScriptPath
        if ($LASTEXITCODE -ne 0) {
            throw "Exit code $LASTEXITCODE"
        }
        $moduleResults.Add([pscustomobject]@{
                Module = $ModuleName
                Passed = $true
            })
        foreach ($id in $AssertionIds) {
            Set-GateResult -GateId $id -Passed $true
        }
        return [pscustomobject]@{
            Passed = $true
            Infra  = $false
            Error  = $null
        }
    } catch {
        $errorMessage = $_.Exception.Message
        $isInfra = Test-InfraFailure -Message $errorMessage
        $moduleResults.Add([pscustomobject]@{
                Module = $ModuleName
                Passed = $false
                Error  = $errorMessage
                Infra  = $isInfra
            })
        foreach ($id in $AssertionIds) {
            $reason = if ($isInfra) { "INFRA: $errorMessage" } else { $errorMessage }
            Set-GateResult -GateId $id -Passed $false -Reason $reason
        }
        if ($isInfra) {
            Add-UniqueItem -List $infraFailures -Value $ModuleName
        } else {
            foreach ($id in $FailConditionIds) {
                Add-UniqueItem -List $triggeredFailConditions -Value $id
            }
        }
        return [pscustomobject]@{
            Passed = $false
            Infra  = $isInfra
            Error  = $errorMessage
        }
    }
}

function Invoke-WeekStage {
    param(
        [string]$ScriptPath,
        [string]$ModuleName,
        [string[]]$AssertionIds,
        [string[]]$FailConditionIds = @(),
        [string[]]$ContainerNames = @()
    )

    Write-Output ""
    Write-Output ("=== {0} preflight ===" -f $ModuleName)
    Wait-DockerReady -Stage "$ModuleName preflight"
    Clear-WeekContainers -ContainerNames $ContainerNames

    $result = Invoke-GateScript -ScriptPath $ScriptPath -ModuleName $ModuleName -AssertionIds $AssertionIds -FailConditionIds $FailConditionIds

    Write-Output ("=== {0} cleanup ===" -f $ModuleName)
    if (Test-DockerReady) {
        Clear-WeekContainers -ContainerNames $ContainerNames
    }

    if ($WeekCooldownSeconds -gt 0) {
        Write-Output ("=== cooldown {0}s ===" -f $WeekCooldownSeconds)
        Start-Sleep -Seconds $WeekCooldownSeconds
    }

    return $result
}

function Test-NoJavaRepairProviderOwnership {
    $javaRoot = Join-Path $root "BackEnd/src/main/java"
    $patterns = @(
        'https://api\.openai\.com',
        'gpt-[A-Za-z0-9\-]+',
        'output_text',
        'Generate a concise repair proposal in JSON',
        'chat/completions',
        '/responses'
    )
    $rgCommand = Get-Command rg -ErrorAction SilentlyContinue
    $useRg = $false
    if ($null -ne $rgCommand -and -not [string]::IsNullOrWhiteSpace($rgCommand.Source) -and (Test-Path $rgCommand.Source)) {
        $useRg = $true
    }
    foreach ($pattern in $patterns) {
        if ($useRg) {
            $matches = & $rgCommand.Source -n --glob "*.java" --pcre2 $pattern $javaRoot 2>$null
            if ($LASTEXITCODE -eq 2) {
                $useRg = $false
            } elseif ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace(($matches -join "`n"))) {
                return $false
            } else {
                continue
            }
        }

        $files = Get-ChildItem -Path $javaRoot -Recurse -Filter "*.java" -ErrorAction SilentlyContinue
        if ($files.Count -eq 0) {
            return $false
        }
        $matches = $files | Select-String -Pattern $pattern -ErrorAction SilentlyContinue
        if ($matches) {
            return $false
        }
    }
    return $true
}

function Test-RepairBoundaryFlow {
    $clientFile = Join-Path $root "BackEnd/src/main/java/com/sage/backend/repair/RepairProposalClient.java"
    $serviceFile = Join-Path $root "Service/planning-pass1/app/main.py"
    $schemaFile = Join-Path $root "Service/planning-pass1/app/schemas.py"

    if (-not (Test-Path $clientFile) -or -not (Test-Path $serviceFile) -or -not (Test-Path $schemaFile)) {
        return $false
    }

    $clientText = Get-Content -Raw $clientFile
    $serviceText = Get-Content -Raw $serviceFile
    $schemaText = Get-Content -Raw $schemaFile

    if ($clientText -notmatch '/repair/proposal') { return $false }
    if ($serviceText -notmatch '@app\.post\("/repair/proposal"') { return $false }
    if ($schemaText -match 'can_resume|required_user_actions|routing|severity') { return $false }
    return $true
}

function Test-RepairAuthorityIsolation {
    $taskService = Join-Path $root "BackEnd/src/main/java/com/sage/backend/task/TaskService.java"
    $repairDispatcher = Join-Path $root "BackEnd/src/main/java/com/sage/backend/repair/RepairDispatcherService.java"
    if (-not (Test-Path $taskService) -or -not (Test-Path $repairDispatcher)) {
        return $false
    }

    $taskText = Get-Content -Raw $taskService
    $dispatcherText = Get-Content -Raw $repairDispatcher

    if ($taskText -notmatch 'refreshWaitingContext\(') { return $false }
    if ($taskText -notmatch 'waitingContext\.path\("can_resume"\)') { return $false }
    if ($dispatcherText -notmatch 'waitingContext.put\("can_resume"') { return $false }
    if ($dispatcherText -notmatch 'String routing = "WAITING_USER"') { return $false }
    return $true
}

$stopAfterInfraFailure = $false

if (-not $SkipWeek4 -and -not $stopAfterInfraFailure) {
    $week4Result = Invoke-WeekStage `
        -ScriptPath (Join-Path $PSScriptRoot 'week4-e2e.ps1') `
        -ModuleName 'week4-e2e' `
        -AssertionIds @('G3') `
        -ContainerNames @('sage-week4-postgres', 'sage-week4-service', 'sage-week4-backend')
    if ($week4Result.Infra) {
        $stopAfterInfraFailure = $true
    }
}

if (-not $SkipWeek5 -and -not $stopAfterInfraFailure) {
    $week5Result = Invoke-WeekStage `
        -ScriptPath (Join-Path $PSScriptRoot 'week5-e2e.ps1') `
        -ModuleName 'week5-e2e' `
        -AssertionIds @('G1', 'G2') `
        -FailConditionIds @('F1') `
        -ContainerNames @('sage-week5-postgres', 'sage-week5-service', 'sage-week5-backend')
    if ($week5Result.Infra) {
        $stopAfterInfraFailure = $true
    }
}

if (-not $SkipWeek6 -and -not $stopAfterInfraFailure) {
    $week6Result = Invoke-WeekStage `
        -ScriptPath (Join-Path $PSScriptRoot 'week6-e2e.ps1') `
        -ModuleName 'week6-e2e' `
        -AssertionIds @('G4', 'G5', 'G6', 'G7', 'G8') `
        -FailConditionIds @('F3', 'F4', 'F5', 'F6') `
        -ContainerNames @('sage-week6-postgres', 'sage-week6-redis', 'sage-week6-service', 'sage-week6-backend')
    if ($week6Result.Infra) {
        $stopAfterInfraFailure = $true
    }
}

if (-not (Test-NoJavaRepairProviderOwnership)) {
    Set-GateResult -GateId 'G9' -Passed $false -Reason "Java LLM provider ownership detected"
    Add-UniqueItem -List $triggeredFailConditions -Value 'F7'
} else {
    Set-GateResult -GateId 'G9' -Passed $true
}

if (-not (Test-RepairBoundaryFlow)) {
    Set-GateResult -GateId 'G9' -Passed $false -Reason "Repair proposal boundary flow missing"
    Add-UniqueItem -List $triggeredFailConditions -Value 'F7'
}

if (-not (Test-RepairAuthorityIsolation)) {
    Add-UniqueItem -List $triggeredFailConditions -Value 'F2'
}

foreach ($gateId in $gateDefinitions.Keys) {
    if (-not $gateResults.ContainsKey($gateId)) {
        Set-GateResult -GateId $gateId -Passed $false -Reason "Gate not executed"
    }
}

Write-Output ""
Write-Output "MODULE RESULTS:"
foreach ($module in $moduleResults) {
    $status = if ($module.Passed) { "PASS" } else { "FAIL" }
    $message = "{0}: {1}" -f $module.Module, $status
    if (-not $module.Passed -and $module.Error) {
        $prefix = if ($module.Infra) { "INFRA" } else { "ERROR" }
        $message = "{0} ({1}: {2})" -f $message, $prefix, $module.Error
    }
    Write-Output $message
}

Write-Output ""
Write-Output "GATE RESULTS:"
foreach ($gateId in $gateDefinitions.Keys) {
    $status = if ($gateResults[$gateId] -eq $true) { "PASS" } else { "FAIL" }
    $line = "{0} {1}: {2}" -f $gateId, $gateDefinitions[$gateId], $status
    if ($status -eq "FAIL" -and $gateReasons.ContainsKey($gateId)) {
        $line = "{0} ({1})" -f $line, $gateReasons[$gateId]
    }
    Write-Output $line
}

Write-Output ""
Write-Output "FAIL CONDITIONS:"
foreach ($failId in $failDefinitions.Keys) {
    $status = if ($triggeredFailConditions.Contains($failId)) { "TRIGGERED" } else { "OK" }
    Write-Output ("{0} {1}: {2}" -f $failId, $failDefinitions[$failId], $status)
}

$failedAssertions = @()
foreach ($gateId in $gateDefinitions.Keys) {
    if ($gateResults[$gateId] -ne $true) {
        $failedAssertions += $gateId
    }
}

Write-Output ""
if ($infraFailures.Count -gt 0) {
    Write-Output "PHASE1_INFRA_FAIL"
    Write-Output ("INFRA_MODULES: " + (($infraFailures | Sort-Object -Unique) -join ','))
    exit 2
}

if ($failedAssertions.Count -eq 0 -and $triggeredFailConditions.Count -eq 0) {
    Write-Output 'PHASE1_PASS'
    exit 0
}

Write-Output 'PHASE1_FAIL'
Write-Output ('FAILED_ASSERTIONS: ' + (($failedAssertions | Sort-Object -Unique) -join ','))
Write-Output ('TRIGGERED_FAIL_CONDITIONS: ' + (($triggeredFailConditions | Sort-Object -Unique) -join ','))
exit 1
