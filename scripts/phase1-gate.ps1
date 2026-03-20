param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$failedAssertions = New-Object System.Collections.Generic.List[string]
$triggeredFailConditions = New-Object System.Collections.Generic.List[string]

function Add-UniqueItem {
    param(
        [System.Collections.Generic.List[string]]$List,
        [string]$Value
    )
    if (-not $List.Contains($Value)) {
        $List.Add($Value)
    }
}

function Invoke-GateScript {
    param(
        [string]$ScriptPath,
        [string[]]$AssertionIds,
        [string[]]$FailConditionIds = @()
    )

    try {
        & powershell.exe -ExecutionPolicy Bypass -File $ScriptPath
    } catch {
        foreach ($id in $AssertionIds) {
            Add-UniqueItem -List $failedAssertions -Value $id
        }
        foreach ($id in $FailConditionIds) {
            Add-UniqueItem -List $triggeredFailConditions -Value $id
        }
    }
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
    foreach ($pattern in $patterns) {
        $matches = rg -n --glob "*.java" --pcre2 $pattern $javaRoot
        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace(($matches -join "`n"))) {
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

Invoke-GateScript -ScriptPath (Join-Path $PSScriptRoot 'week4-e2e.ps1') -AssertionIds @('G3')
Invoke-GateScript -ScriptPath (Join-Path $PSScriptRoot 'week5-e2e.ps1') -AssertionIds @('G1', 'G2') -FailConditionIds @('F1')
Invoke-GateScript -ScriptPath (Join-Path $PSScriptRoot 'week6-e2e.ps1') -AssertionIds @('G4', 'G5', 'G6', 'G7', 'G8') -FailConditionIds @('F3', 'F4', 'F5', 'F6')

if (-not (Test-NoJavaRepairProviderOwnership)) {
    Add-UniqueItem -List $failedAssertions -Value 'G9'
    Add-UniqueItem -List $triggeredFailConditions -Value 'F7'
}

if (-not (Test-RepairBoundaryFlow)) {
    Add-UniqueItem -List $failedAssertions -Value 'G9'
    Add-UniqueItem -List $triggeredFailConditions -Value 'F7'
}

if (-not (Test-RepairAuthorityIsolation)) {
    Add-UniqueItem -List $triggeredFailConditions -Value 'F2'
}

if ($failedAssertions.Count -eq 0 -and $triggeredFailConditions.Count -eq 0) {
    Write-Output 'PHASE1_PASS'
    exit 0
}

Write-Output 'PHASE1_FAIL'
Write-Output ('FAILED_ASSERTIONS: ' + (($failedAssertions | Sort-Object -Unique) -join ','))
Write-Output ('TRIGGERED_FAIL_CONDITIONS: ' + (($triggeredFailConditions | Sort-Object -Unique) -join ','))
exit 1
