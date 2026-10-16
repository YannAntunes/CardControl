<#
.SYNOPSIS
    Gera uma nova chave de assinatura privada para release do CardControl e prepara as credenciais seguras para o GitHub Actions.

.DESCRIPTION
    Este script cria uma keystore JKS criptograficamente segura fora do controle de versao,
    calcula a impressao digital SHA-256 do certificado para verificacao publica e codifica
    o binario em Base64 para cadastro nos segredos de CI/CD.

.PARAMETER OutputDir
    Diretorio local protegido onde a keystore sera salva (padrao: %LOCALAPPDATA%\CardControlSigning).

.PARAMETER Alias
    Alias da chave de release (padrao: cardcontrol).

.PARAMETER SetGitHubSecrets
    Se especificado, valida o acesso antes de gerar a chave, cadastra os 4 secrets e a variable do fingerprint no repositorio.
#>

param(
    [string]$OutputDir = "$env:LOCALAPPDATA\CardControlSigning",
    [string]$Alias = "cardcontrol",
    [switch]$SetGitHubSecrets,
    [switch]$AcknowledgeUpgradeCompatibility
)

$ErrorActionPreference = "Stop"
$Repository = "kauankelvin7/CardControl"

if (-not $AcknowledgeUpgradeCompatibility) {
    throw "Keystore generation blocked. First inventory existing APK certificates and document a validated upgrade strategy, then explicitly pass -AcknowledgeUpgradeCompatibility."
}

if ($SetGitHubSecrets) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw "GitHub CLI (gh) nao encontrado. Instale-o e autentique com gh auth login antes de gerar a chave." }
    & gh auth status *> $null
    if ($LASTEXITCODE -ne 0) { throw "GitHub CLI nao autenticado. Execute gh auth login e tente novamente." }
    & gh repo view $Repository *> $null
    if ($LASTEXITCODE -ne 0) { throw "A conta autenticada nao consegue acessar $Repository." }
    $ExistingSecretsRaw = (& gh secret list --repo $Repository --json name 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel consultar os secrets existentes; a keystore nao sera gerada." }
    $ExistingSecrets = @(($ExistingSecretsRaw | ConvertFrom-Json) | ForEach-Object { $_.name })
    $ExistingVariablesRaw = (& gh variable list --repo $Repository --json name 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel consultar as variables existentes; a keystore nao sera gerada." }
    $ExistingVariables = @(($ExistingVariablesRaw | ConvertFrom-Json) | ForEach-Object { $_.name })
    $SigningNames = @("CARDCONTROL_KEYSTORE_BASE64", "CARDCONTROL_KEYSTORE_PASSWORD", "CARDCONTROL_KEY_ALIAS", "CARDCONTROL_KEY_PASSWORD")
    if (@($ExistingSecrets | Where-Object { $_ -in $SigningNames }).Count -gt 0 -or @($ExistingVariables | Where-Object { $_ -in @("CARDCONTROL_CERT_SHA256", "CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED") }).Count -gt 0) { throw "Configuracao de assinatura ja existe no GitHub. O gerador nao ira substituir credenciais ou fingerprint." }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " CardControl - Gerador Seguro de Chave de Release v2.3.0 " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Localizar keytool
$KeytoolCandidates = @(@(
    "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\keytool.exe",
    "C:\Program Files\Java\jdk-17\bin\keytool.exe",
    "$env:JAVA_HOME\bin\keytool.exe"
) | Where-Object { $_ -and (Test-Path $_) })

$Keytool = if ($KeytoolCandidates.Count -gt 0) { $KeytoolCandidates[0] } else { (Get-Command keytool.exe -ErrorAction SilentlyContinue).Source }

if (-not $Keytool) {
    Write-Error "keytool.exe nao encontrado. Certifique-se de ter um JDK instalado e no PATH."
    exit 1
}

Write-Host "[1/5] keytool localizado: $Keytool" -ForegroundColor Gray

# 2. Preparar diretorio
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}
& icacls.exe $OutputDir /inheritance:r /grant:r "$($env:USERDOMAIN)\$($env:USERNAME):(OI)(CI)F" "SYSTEM:(OI)(CI)F" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel restringir as permissoes do diretorio de assinatura." }

$KeystorePath = Join-Path (Resolve-Path $OutputDir).Path "cardcontrol-release.jks"
$EnvPath = Join-Path (Resolve-Path $OutputDir).Path "release-secrets.env"

if (Test-Path $EnvPath) {
    throw "Um arquivo de credenciais ja existe em $EnvPath. Ele nao sera sobrescrito."
}
if (Test-Path $KeystorePath) {
    throw "Uma keystore ja existe em $KeystorePath. Ela nao sera sobrescrita: use a chave persistente existente ou escolha outro OutputDir somente se uma nova identidade de assinatura tiver sido aprovada."
}

# 3. Gerar senhas se nao fornecidas
# Senha hexadecimal gerada por CSPRNG; nÃ£o Ã© recebida por argumento de linha de comando.
$Bytes = New-Object byte[] 32
$Rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $Rng.GetBytes($Bytes) } finally { $Rng.Dispose() }
$StorePass = [BitConverter]::ToString($Bytes).Replace('-', '')
$KeyPass = $StorePass
[Array]::Clear($Bytes, 0, $Bytes.Length)

Write-Host "[2/5] Gerando par de chaves RSA 2048 bits com validade de 10.000 dias..." -ForegroundColor Gray

$DName = "CN=CardControl, OU=Mobile Security, O=CardControl Project, L=Sao Paulo, ST=SP, C=BR"

$env:CARDCONTROL_KEYSTORE_PASSWORD = $StorePass
$env:CARDCONTROL_KEY_PASSWORD = $KeyPass
try {
    $KeyGenArgs = @(
        "-genkeypair",
        "-v",
        "-keystore", $KeystorePath,
        "-alias", $Alias,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-storepass:env", "CARDCONTROL_KEYSTORE_PASSWORD",
        "-keypass:env", "CARDCONTROL_KEY_PASSWORD",
        "-dname", $DName
    )

    & "$Keytool" @KeyGenArgs | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $KeystorePath)) {
        throw "Falha ao gerar o arquivo de keystore; nenhuma credencial sera cadastrada no GitHub."
    }
    Write-Host "[3/5] Keystore criada com sucesso em: $KeystorePath" -ForegroundColor Green

    $CertInfo = & "$Keytool" -list -v -keystore $KeystorePath -alias $Alias -storepass:env CARDCONTROL_KEYSTORE_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel ler o certificado da keystore gerada." }
    $Sha256Line = ($CertInfo | Select-String "SHA256:").Line
    if (-not $Sha256Line) { throw "Nao foi possivel calcular o fingerprint SHA-256. O provisionamento foi cancelado." }
    $Fingerprint = ($Sha256Line -split "SHA256:")[1].Trim()
    if ($Fingerprint -notmatch "^([A-Fa-f0-9]{2}:){31}[A-Fa-f0-9]{2}$") { throw "O fingerprint SHA-256 retornado e invalido: $Fingerprint" }
} finally {
    Remove-Item Env:\CARDCONTROL_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\CARDCONTROL_KEY_PASSWORD -ErrorAction SilentlyContinue
}

Write-Host "`n>> Impressao Digital do Certificado (SHA-256 Fingerprint):" -ForegroundColor Cyan
Write-Host "   $Fingerprint" -ForegroundColor White
Write-Host ">> Guarde esta impressao digital para validar o APK assinado.`n" -ForegroundColor DarkGray

# 5. Converter Keystore para Base64
$KeystoreBytes = [System.IO.File]::ReadAllBytes($KeystorePath)
$KeystoreBase64 = [Convert]::ToBase64String($KeystoreBytes)

# 6. Salvar dados de segredo localmente no diretorio com ACL restrita
$EnvContent = @"
# CardControl - Segredos de Assinatura de Release (MANTENHA FORA DO GIT)
CARDCONTROL_KEYSTORE_BASE64=$KeystoreBase64
CARDCONTROL_KEYSTORE_PASSWORD=$StorePass
CARDCONTROL_KEY_ALIAS=$Alias
CARDCONTROL_KEY_PASSWORD=$KeyPass
"@
Set-Content -Path $EnvPath -Value $EnvContent -Encoding UTF8
Write-Host "[4/5] Segredos gerados com sucesso." -ForegroundColor Green
Write-Host "      Arquivo com credenciais locais salvo em: $EnvPath" -ForegroundColor DarkGray

# 7. Provisionar no GitHub Actions via gh CLI
if ($SetGitHubSecrets) {
    Write-Host "`n[5/5] Cadastrando secrets e fingerprint confiavel no GitHub..." -ForegroundColor Cyan
    function Set-CardControlSecret([string]$Name, [string]$Value) {
        $Value | & gh secret set $Name --repo $Repository
        if ($LASTEXITCODE -ne 0) { throw "Falha ao cadastrar o secret $Name. A keystore local foi preservada para recuperacao." }
    }
    Set-CardControlSecret "CARDCONTROL_KEYSTORE_BASE64" $KeystoreBase64
    Set-CardControlSecret "CARDCONTROL_KEYSTORE_PASSWORD" $StorePass
    Set-CardControlSecret "CARDCONTROL_KEY_ALIAS" $Alias
    Set-CardControlSecret "CARDCONTROL_KEY_PASSWORD" $KeyPass
    & gh variable set CARDCONTROL_CERT_SHA256 --body $Fingerprint --repo $Repository
    if ($LASTEXITCODE -ne 0) { throw "Falha ao cadastrar a variable CARDCONTROL_CERT_SHA256. A keystore local foi preservada para recuperacao." }
    & gh variable set CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED --body true --repo $Repository
    if ($LASTEXITCODE -ne 0) { throw "Falha ao registrar a decisao explicitada de compatibilidade. A keystore local foi preservada para recuperacao." }

    $ConfiguredSecretsRaw = (& gh secret list --repo $Repository --json name 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel verificar a lista de secrets no GitHub." }
    $ConfiguredSecrets = @(($ConfiguredSecretsRaw | ConvertFrom-Json) | ForEach-Object { $_.name })
    foreach ($Name in @("CARDCONTROL_KEYSTORE_BASE64", "CARDCONTROL_KEYSTORE_PASSWORD", "CARDCONTROL_KEY_ALIAS", "CARDCONTROL_KEY_PASSWORD")) {
        if ($ConfiguredSecrets -notcontains $Name) { throw "O GitHub nao confirmou a existencia do secret $Name." }
    }
    $ConfiguredVariablesRaw = (& gh variable list --repo $Repository --json name,value 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel verificar as variables no GitHub." }
    $ConfiguredVariables = $ConfiguredVariablesRaw | ConvertFrom-Json
    $certVar = foreach ($v in $ConfiguredVariables) { if ($v.name -eq "CARDCONTROL_CERT_SHA256") { $v; break } }
    $ConfiguredFingerprint = $certVar.value
    if (-not $ConfiguredFingerprint -or ($ConfiguredFingerprint -replace ":", "").ToUpperInvariant() -ne ($Fingerprint -replace ":", "").ToUpperInvariant()) { throw "A variable CARDCONTROL_CERT_SHA256 nao foi confirmada com o fingerprint esperado." }
    $compatVar = foreach ($v in $ConfiguredVariables) { if ($v.name -eq "CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED") { $v; break } }
    $ConfiguredCompatibility = $compatVar.value
    if (-not $ConfiguredCompatibility -or "$ConfiguredCompatibility".Trim().ToLowerInvariant() -ne "true") { throw "A variable CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED nao foi confirmada." }
    Write-Host "Secrets e variaveis de release confirmados no repositorio $Repository." -ForegroundColor Green
} else {
    Write-Host "`n[5/5] Nenhum segredo foi enviado ao GitHub." -ForegroundColor Yellow
    Write-Host "Acesse: https://github.com/kauankelvin7/CardControl/settings/secrets/actions" -ForegroundColor White
    Write-Host "Cadastre os 4 Repository Secrets usando o arquivo local protegido: $EnvPath" -ForegroundColor White
    Write-Host "Cadastre a Repository Variable CARDCONTROL_CERT_SHA256 com o fingerprint exibido acima." -ForegroundColor White
    Write-Host "Defina CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED=true, conforme decisao documentada, antes da release." -ForegroundColor White
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " ARQUIVOS LOCAIS PREPARADOS                              " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Mantenha um backup OFFLINE seguro da keystore e das credenciais." -ForegroundColor Yellow
Write-Host "Nenhuma release foi publicada. Nunca commite a keystore ou release-secrets.env.`n" -ForegroundColor Red
