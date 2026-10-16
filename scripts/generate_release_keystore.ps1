<#
.SYNOPSIS
    Gera uma nova chave de assinatura privada para release do CardControl e prepara as credenciais seguras para o GitHub Actions.

.DESCRIPTION
    Este script cria uma keystore JKS criptograficamente segura fora do controle de versao,
    calcula a impressao digital SHA-256 do certificado para verificacao publica e codifica
    o binario em Base64 para cadastro nos segredos de CI/CD.

.PARAMETER OutputDir
    Diretorio onde a keystore sera salva (padrao: .\temp_keystore, ignorado pelo Git).

.PARAMETER Alias
    Alias da chave de release (padrao: cardcontrol).

.PARAMETER SetGitHubSecrets
    Se especificado e o GitHub CLI (gh) estiver autenticado, cadastra automaticamente os 4 segredos no repositorio.
#>

param(
    [string]$OutputDir = ".\temp_keystore",
    [string]$Alias = "cardcontrol",
    [string]$StorePass = "",
    [string]$KeyPass = "",
    [switch]$SetGitHubSecrets,
    [switch]$Force,
    [switch]$NonInteractive
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " CardControl - Gerador Seguro de Chave de Release v2.2.0 " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Localizar keytool
$KeytoolCandidates = @(
    "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\keytool.exe",
    "C:\Program Files\Java\jdk-17\bin\keytool.exe",
    "$env:JAVA_HOME\bin\keytool.exe"
) | Where-Object { $_ -and (Test-Path $_) }

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

$KeystorePath = Join-Path (Resolve-Path $OutputDir).Path "cardcontrol-release.jks"

if (Test-Path $KeystorePath) {
    if (-not $Force -and -not $NonInteractive) {
        Write-Host "[AVISO] O arquivo $KeystorePath ja existe!" -ForegroundColor Yellow
        $Confirm = Read-Host "Deseja sobrescrever? (S/N)"
        if ($Confirm -ne "S" -and $Confirm -ne "s") {
            Write-Host "Operacao cancelada pelo usuario." -ForegroundColor Yellow
            exit 0
        }
    }
    Remove-Item $KeystorePath -Force
}

# 3. Gerar senhas se nao fornecidas
if ([string]::IsNullOrWhiteSpace($StorePass)) {
    # Gera senha forte aleatoria com caracteres compativeis
    $Bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($Bytes)
    $StorePass = [Convert]::ToBase64String($Bytes) -replace '[^a-zA-Z0-9]', 'X'
}

if ([string]::IsNullOrWhiteSpace($KeyPass)) {
    $KeyPass = $StorePass
}

Write-Host "[2/5] Gerando par de chaves RSA 2048 bits com validade de 10.000 dias..." -ForegroundColor Gray

$DName = "CN=CardControl, OU=Mobile Security, O=CardControl Project, L=Sao Paulo, ST=SP, C=BR"

$KeyGenArgs = @(
    "-genkeypair",
    "-v",
    "-keystore", $KeystorePath,
    "-alias", $Alias,
    "-keyalg", "RSA",
    "-keysize", "2048",
    "-validity", "10000",
    "-storepass", $StorePass,
    "-keypass", $KeyPass,
    "-dname", $DName
)

& "$Keytool" @KeyGenArgs | Out-Null

if (-not (Test-Path $KeystorePath)) {
    Write-Error "Falha ao gerar o arquivo de keystore."
    exit 1
}

Write-Host "[3/5] Keystore criada com sucesso em: $KeystorePath" -ForegroundColor Green

# 4. Extrair Fingerprint SHA-256
$CertInfo = & "$Keytool" -list -v -keystore $KeystorePath -alias $Alias -storepass $StorePass
$Sha256Line = ($CertInfo | Select-String "SHA256:").Line
$Fingerprint = if ($Sha256Line) { ($Sha256Line -split "SHA256:")[1].Trim() } else { "Nao identificado" }

Write-Host "`n>> Impressao Digital do Certificado (SHA-256 Fingerprint):" -ForegroundColor Cyan
Write-Host "   $Fingerprint" -ForegroundColor White
Write-Host ">> Guarde esta impressao digital para validar o APK assinado.`n" -ForegroundColor DarkGray

# 5. Converter Keystore para Base64
$KeystoreBytes = [System.IO.File]::ReadAllBytes($KeystorePath)
$KeystoreBase64 = [Convert]::ToBase64String($KeystoreBytes)

# 6. Salvar dados de segredo localmente para contingencia (em arquivo protegido e ignorado pelo Git)
$EnvPath = Join-Path (Resolve-Path $OutputDir).Path "release-secrets.env"
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
$GhAvailable = (Get-Command gh -ErrorAction SilentlyContinue) -ne $null

if ($SetGitHubSecrets -or ($GhAvailable -and -not $NonInteractive)) {
    if (-not $SetGitHubSecrets -and -not $NonInteractive) {
        $ConfigureNow = Read-Host "Deseja cadastrar os segredos automaticamente no repositorio GitHub via gh CLI? (S/N)"
        if ($ConfigureNow -eq "S" -or $ConfigureNow -eq "s") {
            $SetGitHubSecrets = $true
        }
    }

    if ($SetGitHubSecrets) {
        Write-Host "`n[5/5] Cadastrando segredos no GitHub Actions..." -ForegroundColor Cyan
        
        $KeystoreBase64 | gh secret set CARDCONTROL_KEYSTORE_BASE64
        $StorePass | gh secret set CARDCONTROL_KEYSTORE_PASSWORD
        $Alias | gh secret set CARDCONTROL_KEY_ALIAS
        $KeyPass | gh secret set CARDCONTROL_KEY_PASSWORD

        Write-Host ">> SUCESSO: Todos os 4 segredos foram cadastrados no GitHub Actions!" -ForegroundColor Green
        Write-Host "   - CARDCONTROL_KEYSTORE_BASE64" -ForegroundColor White
        Write-Host "   - CARDCONTROL_KEYSTORE_PASSWORD" -ForegroundColor White
        Write-Host "   - CARDCONTROL_KEY_ALIAS" -ForegroundColor White
        Write-Host "   - CARDCONTROL_KEY_PASSWORD" -ForegroundColor White
    }
}

if (-not $SetGitHubSecrets) {
    Write-Host "`n[5/5] Instrucoes para cadastro manual nos Segredos do GitHub:" -ForegroundColor Yellow
    Write-Host "Acesse: https://github.com/kauankelvin7/CardControl/settings/secrets/actions" -ForegroundColor White
    Write-Host "E crie os 4 Repository Secrets conforme informados acima ou no arquivo $EnvPath." -ForegroundColor White
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " CONFIGURACAO CONCLUIDA COM SUCESSO!                      " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Lembre-se: mantenha um backup OFFLINE seguro de '$KeystorePath'." -ForegroundColor Yellow
Write-Host "Nunca commite o arquivo .jks, .keystore ou .env no Git.`n" -ForegroundColor Red
