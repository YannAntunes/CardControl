<#
.SYNOPSIS
CardControl Validation Runner
Executes acceptance tests and debug build compilation in accordance with CardControl AGENTS.md rules.

.PARAMETER TestsOnly
Run only unit tests (skip assembleDebug).

.PARAMETER AssembleOnly
Run only assembleDebug (skip unit tests).
#>
param(
    [switch]$TestsOnly,
    [switch]$AssembleOnly
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path

# 1. Resolve JDK
$CandidateJdks = @(
    @(
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot",
        "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr",
        $env:JAVA_HOME
    ) | Where-Object { $_ -and (Test-Path "$_\bin\java.exe") }
)

if ($CandidateJdks.Count -gt 0) {
    $SelectedJdk = $CandidateJdks[0]
    $env:JAVA_HOME = $SelectedJdk
    Write-Host "[CardControl Verify] Using JAVA_HOME: $SelectedJdk" -ForegroundColor Cyan
} else {
    Write-Host "[CardControl Verify] JAVA_HOME not explicitly set; relying on system PATH." -ForegroundColor Yellow
}
# 2. Resolve Android SDK
$SdkCandidates = @(
    @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        "$env:LOCALAPPDATA\Android\Sdk",
        "C:\Android\Sdk"
    ) | Where-Object { $_ -and (Test-Path $_) }
)

$LocalProperties = Join-Path $ProjectRoot "local.properties"
$HasLocalProperties = Test-Path $LocalProperties

if ($SdkCandidates.Count -gt 0) {
    $SelectedSdk = $SdkCandidates[0]
    $env:ANDROID_HOME = $SelectedSdk
    Write-Host "[CardControl Verify] Using ANDROID_HOME: $SelectedSdk" -ForegroundColor Cyan
} elseif ($HasLocalProperties) {
    Write-Host "[CardControl Verify] Using Android SDK configured in local.properties" -ForegroundColor Cyan
} else {
    Write-Host "[CardControl Verify] Warning: Neither ANDROID_HOME nor local.properties found." -ForegroundColor Yellow
    Write-Host "                      To build Android projects locally, configure 'local.properties' with:" -ForegroundColor Yellow
    Write-Host "                      sdk.dir=C:\\path\\to\\android\\sdk (or install Android Command-line Tools / SDK)." -ForegroundColor Yellow
}

$Gradlew = Join-Path $ProjectRoot "gradlew.bat"
if (-not (Test-Path $Gradlew)) {
    Write-Error "gradlew.bat not found at $Gradlew"
    exit 1
}

$Success = $true

# 2. Run Unit Tests (SecurityAndDomainTestHarness)
if (-not $AssembleOnly) {
    Write-Host "`n=== [1/2] Running Unit Tests (SecurityAndDomainTestHarness) ===" -ForegroundColor Green
    $TestCmd = "& `"$Gradlew`" testDebugUnitTest --console=plain `"-Pkotlin.compiler.execution.strategy=in-process`""
    Write-Host "Command: $TestCmd" -ForegroundColor DarkGray
    
    Push-Location $ProjectRoot
    try {
        & "$Gradlew" testDebugUnitTest --console=plain "-Pkotlin.compiler.execution.strategy=in-process"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "`n[FAIL] Unit tests failed with exit code $LASTEXITCODE." -ForegroundColor Red
            $Success = $false
        } else {
            Write-Host "`n[PASS] All unit tests passed successfully." -ForegroundColor Green
        }
    } finally {
        Pop-Location
    }

    if (-not $Success) {
        exit 1
    }
}

# 3. Assemble Debug Build
if (-not $TestsOnly) {
    Write-Host "`n=== [2/2] Assembling Debug APK ===" -ForegroundColor Green
    $BuildCmd = "& `"$Gradlew`" assembleDebug --console=plain --max-workers=2 `"-Pkotlin.compiler.execution.strategy=in-process`""
    Write-Host "Command: $BuildCmd" -ForegroundColor DarkGray

    Push-Location $ProjectRoot
    try {
        & "$Gradlew" assembleDebug --console=plain --max-workers=2 "-Pkotlin.compiler.execution.strategy=in-process"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "`n[FAIL] assembleDebug failed with exit code $LASTEXITCODE." -ForegroundColor Red
            $Success = $false
        } else {
            $ApkPath = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
            if (Test-Path $ApkPath) {
                $ApkSize = (Get-Item $ApkPath).Length / 1MB
                Write-Host "`n[PASS] Debug APK created successfully: $ApkPath ($('{0:N2}' -f $ApkSize) MB)" -ForegroundColor Green
            } else {
                Write-Host "`n[PASS] assembleDebug succeeded." -ForegroundColor Green
            }
        }
    } finally {
        Pop-Location
    }

    if (-not $Success) {
        exit 1
    }
}

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host " [CardControl] All validation gates passed! " -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
exit 0
