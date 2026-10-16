# Provision the already-created persistent CardControl signing identity to GitHub.
# This script never generates, prints, or overwrites the local keystore/credentials file.
[CmdletBinding()]
param(
    [string]$OutputDir = "$env:LOCALAPPDATA\CardControlSigning",
    [string]$Repository = "kauankelvin7/CardControl",
    [switch]$AcknowledgeNoExistingInstallations,
    [switch]$ReplaceExistingConfiguration
)

$ErrorActionPreference = "Stop"

if (-not $AcknowledgeNoExistingInstallations) {
    throw "Stopped. Pass -AcknowledgeNoExistingInstallations only after confirming no device has the previous APK installed."
}

$Gh = (Get-Command gh.exe -ErrorAction SilentlyContinue).Source
if (-not $Gh) {
    $Candidate = Join-Path $HOME "tools\gh-2.102.0\bin\gh.exe"
    if (Test-Path $Candidate) { $Gh = $Candidate }
}
if (-not $Gh) { throw "GitHub CLI (gh.exe) is not installed. Install it and authenticate with 'gh auth login --web'." }

$KeyPath = Join-Path $OutputDir "cardcontrol-release.jks"
$EnvPath = Join-Path $OutputDir "release-secrets.env"
if (-not (Test-Path $KeyPath -PathType Leaf) -or -not (Test-Path $EnvPath -PathType Leaf)) {
    throw "Expected persistent keystore and local credential file were not found. No GitHub changes were made."
}

# Read local credentials without printing any values.
$Values = @{}
foreach ($Line in [System.IO.File]::ReadAllLines($EnvPath)) {
    if ([string]::IsNullOrWhiteSpace($Line) -or $Line.StartsWith("#")) { continue }
    $Separator = $Line.IndexOf("=")
    if ($Separator -le 0) { throw "Malformed local credentials file. No GitHub changes were made." }
    $Values[$Line.Substring(0, $Separator)] = $Line.Substring($Separator + 1)
}
$Required = @("CARDCONTROL_KEYSTORE_BASE64", "CARDCONTROL_KEYSTORE_PASSWORD", "CARDCONTROL_KEY_ALIAS", "CARDCONTROL_KEY_PASSWORD")
foreach ($Name in $Required) {
    if (-not $Values.ContainsKey($Name) -or [string]::IsNullOrWhiteSpace($Values[$Name])) {
        $Values.Clear()
        throw "Required credential field $Name is missing. No GitHub changes were made."
    }
}

try {
    $KeystoreBase64 = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($KeyPath))
    if ($KeystoreBase64 -cne $Values["CARDCONTROL_KEYSTORE_BASE64"]) {
        throw "Local credentials do not match the keystore file. No GitHub changes were made."
    }

    $Keytool = (Get-Command keytool.exe -ErrorAction SilentlyContinue).Source
    if (-not $Keytool -and $env:JAVA_HOME) {
        $Candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
        if (Test-Path $Candidate) { $Keytool = $Candidate }
    }
    if (-not $Keytool) { throw "keytool.exe was not found. No GitHub changes were made." }

    $env:CARDCONTROL_KEYSTORE_PASSWORD = $Values["CARDCONTROL_KEYSTORE_PASSWORD"]
    try {
        $CertLines = @(& $Keytool -list -v -keystore $KeyPath -alias $Values["CARDCONTROL_KEY_ALIAS"] -storepass:env CARDCONTROL_KEYSTORE_PASSWORD 2>&1)
        if ($LASTEXITCODE -ne 0) { throw "The local keystore could not be opened using the saved alias and password." }
    }
    finally {
        Remove-Item Env:\CARDCONTROL_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    }

    $FingerprintLine = $CertLines | Where-Object { "$_" -match "SHA256:\s*([0-9A-Fa-f:]+)" } | Select-Object -First 1
    if (-not $FingerprintLine -or "$FingerprintLine" -notmatch "SHA256:\s*((?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})") {
        throw "Could not validate the local certificate SHA-256 fingerprint."
    }
    $Fingerprint = $Matches[1].ToUpperInvariant()
    $CertLines = $null
    $KeystoreBase64 = $null

    $Auth = @(& $Gh auth status 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "GitHub CLI is not authenticated. Run 'gh auth login --web' and retry." }
    $RepoInfo = @(& $Gh repo view $Repository --json nameWithOwner,viewerPermission 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "The authenticated account cannot access $Repository." }

    $ExistingSecretsRaw = (& $Gh secret list --repo $Repository --json name 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Could not inspect existing repository secrets; no changes were made." }
    $ExistingSecrets = @(($ExistingSecretsRaw | ConvertFrom-Json) | ForEach-Object { $_.name })
    $ExistingVariablesRaw = (& $Gh variable list --repo $Repository --json name 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Could not inspect existing repository variables; no changes were made." }
    $ExistingVariables = @(($ExistingVariablesRaw | ConvertFrom-Json) | ForEach-Object { $_.name })

    $ExistingSigningSecrets = @($ExistingSecrets | Where-Object { $_ -in $Required })
    $SigningVariables = @("CARDCONTROL_CERT_SHA256", "CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED")
    $ExistingSigningVariables = @($ExistingVariables | Where-Object { $_ -in $SigningVariables })
    if (($ExistingSigningSecrets.Count -gt 0 -or $ExistingSigningVariables.Count -gt 0) -and -not $ReplaceExistingConfiguration) {
        throw "Signing secrets/variables already exist. Re-run with -ReplaceExistingConfiguration only to intentionally replace the prior signing setup."
    }

    # Write secrets through stdin so their values never appear in process arguments or console output.
    foreach ($Name in $Required) {
        $StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $StartInfo.FileName = $Gh
        $StartInfo.Arguments = "secret set $Name --repo $Repository"
        $StartInfo.UseShellExecute = $false
        $StartInfo.CreateNoWindow = $true
        $StartInfo.RedirectStandardInput = $true
        $StartInfo.RedirectStandardOutput = $true
        $StartInfo.RedirectStandardError = $true
        $Process = [System.Diagnostics.Process]::new()
        $Process.StartInfo = $StartInfo
        [void]$Process.Start()
        $StdOutTask = $Process.StandardOutput.ReadToEndAsync()
        $StdErrTask = $Process.StandardError.ReadToEndAsync()
        $Process.StandardInput.Write($Values[$Name])
        $Process.StandardInput.Close()
        $Process.WaitForExit()
        $StdOut = $StdOutTask.Result
        $StdErr = $StdErrTask.Result
        $ExitCode = $Process.ExitCode
        $Process.Dispose()
        if ($ExitCode -ne 0) {
            throw "GitHub failed to set signing secret $Name (exit $ExitCode). Values were not printed; rerun after checking repository settings."
        }
    }

    & $Gh variable set CARDCONTROL_CERT_SHA256 --body $Fingerprint --repo $Repository
    if ($LASTEXITCODE -ne 0) { throw "GitHub failed to store CARDCONTROL_CERT_SHA256." }
    & $Gh variable set CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED --body true --repo $Repository
    if ($LASTEXITCODE -ne 0) { throw "GitHub failed to store CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED." }

    $VerifiedSecretsRaw = (& $Gh secret list --repo $Repository --json name 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Could not verify the signing secrets after setup." }
    $VerifiedSecrets = @(($VerifiedSecretsRaw | ConvertFrom-Json) | ForEach-Object { $_.name })
    foreach ($Name in $Required) {
        if ($VerifiedSecrets -notcontains $Name) { throw "GitHub did not confirm signing secret $Name." }
    }
    $VerifiedVariablesRaw = (& $Gh variable list --repo $Repository --json name,value 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Could not verify the repository variables after setup." }
    $VerifiedVariables = $VerifiedVariablesRaw | ConvertFrom-Json
    $certVar = foreach ($v in $VerifiedVariables) { if ($v.name -eq "CARDCONTROL_CERT_SHA256") { $v; break } }
    $VerifiedFingerprint = $certVar.value
    if (-not $VerifiedFingerprint -or ($VerifiedFingerprint -replace ":", "").ToUpperInvariant() -ne ($Fingerprint -replace ":", "").ToUpperInvariant()) {
        throw "GitHub did not confirm the expected signing certificate fingerprint."
    }
    $compatVar = foreach ($v in $VerifiedVariables) { if ($v.name -eq "CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED") { $v; break } }
    $VerifiedDecision = $compatVar.value
    if (-not $VerifiedDecision -or "$VerifiedDecision".Trim().ToLowerInvariant() -ne "true") {
        throw "GitHub did not confirm the no-existing-installations decision."
    }
    Write-Output "Persistent signing configuration verified for $Repository."
    Write-Output "Four secret names and both repository variables were confirmed; secret values were not displayed."
    Write-Output "Certificate SHA-256: $Fingerprint"
    Write-Output "No release was published."
}
finally {
    $Values.Clear()
    $KeystoreBase64 = $null
    $Fingerprint = $null
}
