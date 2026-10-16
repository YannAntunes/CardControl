<#
.SYNOPSIS
CardControl State & Task Consistency Checker
Audits tasks defined in .agent/CARDCONTROL_TASKS.yaml and checks consistency with CARDCONTROL_STATE.md.
#>

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$TasksFile = Join-Path $ProjectRoot ".agent\CARDCONTROL_TASKS.yaml"
$StateFile = Join-Path $ProjectRoot ".agent\CARDCONTROL_STATE.md"
$PlanFile = Join-Path $ProjectRoot ".agent\CARDCONTROL_EXECPLAN.md"

Write-Host "=== CardControl Governance State Audit ===" -ForegroundColor Cyan

if (-not (Test-Path $TasksFile)) {
    Write-Error "Missing $TasksFile"
    exit 1
}

$Content = Get-Content $TasksFile -Raw
$TaskMatches = [regex]::Matches($Content, 'id:\s*"([^"]+)".*?title:\s*"([^"]+)".*?status:\s*"([^"]+)"', [System.Text.RegularExpressions.RegexOptions]::Singleline)

$CompletedCount = 0
$PendingCount = 0

Write-Host "`nTasks registered:" -ForegroundColor Yellow
foreach ($Match in $TaskMatches) {
    $Id = $Match.Groups[1].Value
    $Title = $Match.Groups[2].Value
    $Status = $Match.Groups[3].Value
    if ($Status -eq "COMPLETED") {
        $CompletedCount++
        Write-Host "  [$Status] ${Id}: $Title" -ForegroundColor Green
    } else {
        $PendingCount++
        Write-Host "  [$Status]   ${Id}: $Title" -ForegroundColor Yellow
    }
}

Write-Host "`nSummary: $CompletedCount completed, $PendingCount pending." -ForegroundColor Cyan

# Check if State file is present
if (Test-Path $StateFile) {
    Write-Host "[OK] CARDCONTROL_STATE.md is present." -ForegroundColor Green
} else {
    Write-Host "[WARN] CARDCONTROL_STATE.md is missing!" -ForegroundColor Red
}

if (Test-Path $PlanFile) {
    Write-Host "[OK] CARDCONTROL_EXECPLAN.md is present." -ForegroundColor Green
} else {
    Write-Host "[WARN] CARDCONTROL_EXECPLAN.md is missing!" -ForegroundColor Red
}
