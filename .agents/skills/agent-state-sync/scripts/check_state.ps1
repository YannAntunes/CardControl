<#
.SYNOPSIS
    CardControl State, Task & Repository Consistency Checker (Padrao Leve)
    Audita integridade de governanca, consistencia entre Git (SHA/Branch),
    arquivos de estado em .agents/, schemas Room e integridade das skills.
#>

param(
    [switch]$Detailed
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir '..\..\..\..')).Path

Set-Location $ProjectRoot

Write-Host '==========================================================' -ForegroundColor Cyan
Write-Host ' CardControl - Auditoria de Estado e Governanca (Leve)    ' -ForegroundColor Green
Write-Host '==========================================================' -ForegroundColor Cyan

$Inconsistencies = @()

# ---------------------------------------------------------
# 1. Verificacao de Diretorio Unico de Governanca (.agents vs .agent)
# ---------------------------------------------------------
Write-Host '>> 1. Estrutura de Diretorios:' -ForegroundColor Cyan
if (Test-Path '.agent') {
    $Inconsistencies += "Pasta legada duplicada '.agent' detectada! A governanca deve residir estritamente em '.agents'."
    Write-Host '   [FALHA] Pasta legada .agent ainda presente no workspace.' -ForegroundColor Red
} else {
    Write-Host '   [OK] Pasta de governanca unica e canonica: .agents' -ForegroundColor Green
}

if (-not (Test-Path '.agents')) {
    $Inconsistencies += "Pasta canonica '.agents' ausente!"
    Write-Host '   [FALHA] Pasta canonica .agents nao encontrada.' -ForegroundColor Red
}

# ---------------------------------------------------------
# 2. Verificacao de Arquivos Obrigatorios de Governanca
# ---------------------------------------------------------
Write-Host "`n>> 2. Arquivos Obrigatorios de Governanca:" -ForegroundColor Cyan
$RequiredGovernanceFiles = @(
    '.agents\CARDCONTROL_STATE.md',
    '.agents\CARDCONTROL_TASKS.yaml',
    '.agents\CARDCONTROL_EXECPLAN.md',
    '.agents\CARDCONTROL_DECISIONS.md',
    'AGENTS.md'
)

foreach ($File in $RequiredGovernanceFiles) {
    if (-not (Test-Path $File)) {
        $Inconsistencies += "Arquivo obrigatorio ausente: $File"
        Write-Host "   [FALHA] Ausente: $File" -ForegroundColor Red
    } else {
        $FileSize = (Get-Item $File).Length
        if ($FileSize -lt 50) {
            $Inconsistencies += "Arquivo de governanca vazio ou corrompido: $File ($FileSize bytes)"
            Write-Host "   [FALHA] Tamanho insuficiente: $File ($FileSize bytes)" -ForegroundColor Red
        } else {
            Write-Host "   [OK] Presente e integro: $File ($FileSize bytes)" -ForegroundColor Green
        }
    }
}

# ---------------------------------------------------------
# 3. Rastreabilidade Git: Branch e Commit SHA
# ---------------------------------------------------------
Write-Host "`n>> 3. Rastreabilidade Git vs Estado:" -ForegroundColor Cyan
$GitBranch = ''
$GitSha = ''
$GitStatusShort = ''
try {
    $RawBranch = git branch --show-current
    $GitBranch = if ($RawBranch) { $RawBranch.Trim() } else { '' }
    $RawSha = git rev-parse --short HEAD
    $GitSha = if ($RawSha) { $RawSha.Trim() } else { '' }
    $RawStatus = git status --porcelain
    $GitStatusShort = if ($RawStatus) { ($RawStatus -join "`n").Trim() } else { '' }
} catch {
    $Inconsistencies += "Falha ao consultar estado do repositorio Git: $_"
}

Write-Host "   Branch Git atual: $GitBranch" -ForegroundColor White
Write-Host "   HEAD Git SHA:     $GitSha" -ForegroundColor White

if ([string]::IsNullOrWhiteSpace($GitStatusShort)) {
    Write-Host '   Working Tree:     Limpa (sem alteracoes pendentes)' -ForegroundColor Green
} else {
    $Lines = $GitStatusShort -split "`r?`n"
    $ChangedCount = $Lines.Count
    Write-Host "   Working Tree:     $ChangedCount arquivo(s) modificado(s)/pendente(s)" -ForegroundColor Yellow
}

# Verificacao de alinhamento com CARDCONTROL_STATE.md
if (Test-Path '.agents\CARDCONTROL_STATE.md') {
    $StateContent = Get-Content '.agents\CARDCONTROL_STATE.md' -Raw

    # Valida branch
    if ($StateContent -match 'Branch ativa:\s*`([^`]+)`') {
        $StateBranch = $Matches[1].Trim()
        if ($StateBranch -ne $GitBranch) {
            $Inconsistencies += "Divergencia de branch: Git esta em '$GitBranch', mas CARDCONTROL_STATE.md declara '$StateBranch'."
            Write-Host "   [FALHA] Branch divergente: Git='$GitBranch' vs STATE='$StateBranch'" -ForegroundColor Red
        } else {
            Write-Host "   [OK] Branch alinhada com CARDCONTROL_STATE.md: $GitBranch" -ForegroundColor Green
        }
    } else {
        $Inconsistencies += "CARDCONTROL_STATE.md nao contem a declaracao de 'Branch ativa: `...`'."
        Write-Host "   [FALHA] Nao foi possivel extrair 'Branch ativa' de CARDCONTROL_STATE.md" -ForegroundColor Red
    }

    # Valida HEAD SHA / Commit base (padrão Leve: verifica presença no log da branch)
    if ($StateContent -match '(?:HEAD SHA|Commit base):\s*`?([0-9a-fA-F]{7,40})`?') {
        $DeclaredSha = $Matches[1].Trim()
        $RecentShas = @((git log -n 10 --pretty=format:"%h") -split "`r?`n")
        $IsMatch = $false
        foreach ($Sha in $RecentShas) {
            $CleanSha = $Sha.Trim()
            if ($CleanSha -and ($CleanSha.StartsWith($DeclaredSha) -or $DeclaredSha.StartsWith($CleanSha))) {
                $IsMatch = $true
                break
            }
        }
        if (-not $IsMatch) {
            $Inconsistencies += "Divergencia de SHA: SHA declarado '$DeclaredSha' nao encontrado nos ultimos 10 commits da branch."
            Write-Host "   [FALHA] SHA divergente: Git HEAD='$GitSha' vs STATE='$DeclaredSha'" -ForegroundColor Red
        } else {
            Write-Host "   [OK] SHA rastreado no historico recente da branch: $DeclaredSha" -ForegroundColor Green
        }
    } else {
        $Inconsistencies += "CARDCONTROL_STATE.md nao declara 'HEAD SHA: `...`' ou 'Commit base: `...`'."
        Write-Host "   [FALHA] Declaracao de SHA ausente em CARDCONTROL_STATE.md" -ForegroundColor Red
    }
}

# ---------------------------------------------------------
# 4. Consistência de Versao (Gradle vs TASKS.yaml vs STATE.md)
# ---------------------------------------------------------
Write-Host "`n>> 4. Consistencia de Versao:" -ForegroundColor Cyan
$GradleVersionName = ''
$GradleVersionCode = ''
if (Test-Path 'app\build.gradle.kts') {
    $GradleContent = Get-Content 'app\build.gradle.kts' -Raw
    if ($GradleContent -match 'versionName\s*=\s*"([^"]+)"') { $GradleVersionName = $Matches[1].Trim() }
    if ($GradleContent -match 'versionCode\s*=\s*(\d+)') { $GradleVersionCode = $Matches[1].Trim() }
}

$TasksVersion = ''
if (Test-Path '.agents\CARDCONTROL_TASKS.yaml') {
    $TasksContent = Get-Content '.agents\CARDCONTROL_TASKS.yaml' -Raw
    if ($TasksContent -match 'version:\s*"([^"]+)"') { $TasksVersion = $Matches[1].Trim() }
}

Write-Host "   Gradle versionName: $GradleVersionName (versionCode: $GradleVersionCode)" -ForegroundColor White
Write-Host "   TASKS.yaml version: $TasksVersion" -ForegroundColor White

if (-not $GradleVersionName) {
    $Inconsistencies += "Nao foi possivel extrair versionName de app/build.gradle.kts"
    Write-Host '   [FALHA] versionName ausente em app/build.gradle.kts' -ForegroundColor Red
}

if (-not $TasksVersion) {
    $Inconsistencies += "Nao foi possivel extrair version de .agents/CARDCONTROL_TASKS.yaml"
    Write-Host '   [FALHA] version ausente em .agents/CARDCONTROL_TASKS.yaml' -ForegroundColor Red
}

if ($GradleVersionName -and $TasksVersion) {
    if ($GradleVersionName -ne $TasksVersion) {
        $Inconsistencies += "Divergencia de versao: build.gradle.kts ($GradleVersionName) != TASKS.yaml ($TasksVersion)"
        Write-Host "   [FALHA] Versao em build.gradle.kts ($GradleVersionName) != TASKS.yaml ($TasksVersion)" -ForegroundColor Red
    } else {
        Write-Host "   [OK] Versoes alinhadas: v$GradleVersionName" -ForegroundColor Green
    }
}

# ---------------------------------------------------------
# 5. Validacao do Schema Room SQLite
# ---------------------------------------------------------
Write-Host "`n>> 5. Integridade do Schema Room SQLite:" -ForegroundColor Cyan
$RoomDbVersion = ''
if (Test-Path 'app\src\main\java\com\cardcontrol\app\data\local\AppDatabase.kt') {
    $AppDbContent = Get-Content 'app\src\main\java\com\cardcontrol\app\data\local\AppDatabase.kt' -Raw
    if ($AppDbContent -match 'version\s*=\s*(\d+)') {
        $RoomDbVersion = $Matches[1].Trim()
    }
}

if ($RoomDbVersion) {
    $SchemaFile = "app\schemas\com.cardcontrol.app.data.local.AppDatabase\$RoomDbVersion.json"
    if (-not (Test-Path $SchemaFile)) {
        $Inconsistencies += "Schema Room exportado ausente para versao ${RoomDbVersion}: $SchemaFile"
        Write-Host "   [FALHA] Schema Room exportado ausente: $SchemaFile" -ForegroundColor Red
    } else {
        Write-Host "   [OK] Schema Room v$RoomDbVersion exportado e integro: $SchemaFile" -ForegroundColor Green
    }

    # Verifica se a migracao existe se Room > 1
    if ([int]$RoomDbVersion -gt 1) {
        $PrevVersion = [int]$RoomDbVersion - 1
        $ExpectedMigration = "MIGRATION_${PrevVersion}_${RoomDbVersion}"
        if ($AppDbContent -notmatch $ExpectedMigration) {
            $Inconsistencies += "Declaracao de migracao $ExpectedMigration ausente em AppDatabase.kt"
            Write-Host "   [FALHA] Migracao $ExpectedMigration ausente em AppDatabase.kt" -ForegroundColor Red
        } else {
            Write-Host "   [OK] Migracao $ExpectedMigration presente e registrada em AppDatabase.kt" -ForegroundColor Green
        }
    }
} else {
    $Inconsistencies += "Nao foi possivel detectar versao do Room em AppDatabase.kt"
    Write-Host '   [FALHA] Versao do Room nao encontrada em AppDatabase.kt' -ForegroundColor Red
}

# ---------------------------------------------------------
# 6. Auditoria do Grafo de Tarefas (.agents/CARDCONTROL_TASKS.yaml)
# ---------------------------------------------------------
Write-Host "`n>> 6. Auditoria do Grafo de Tarefas:" -ForegroundColor Cyan
if (Test-Path '.agents\CARDCONTROL_TASKS.yaml') {
    $TaskMatches = [regex]::Matches($TasksContent, '-\s+id:\s*"([^"]+)".*?title:\s*"([^"]+)".*?status:\s*"([^"]+)"', [System.Text.RegularExpressions.RegexOptions]::Singleline)

    $AllIds = @{}
    $CompletedIds = @{}
    $CompletedCount = 0
    $PendingCount = 0
    $ValidStatuses = @('COMPLETED', 'IN_PROGRESS', 'PENDING', 'BLOCKED')

    foreach ($Match in $TaskMatches) {
        $Id = $Match.Groups[1].Value.Trim()
        $Title = $Match.Groups[2].Value.Trim()
        $Status = $Match.Groups[3].Value.Trim()

        if ($AllIds.ContainsKey($Id)) {
            $Inconsistencies += "ID de tarefa duplicado no YAML: $Id"
            Write-Host "   [DUPLICADO] $Id" -ForegroundColor Red
        }
        $AllIds[$Id] = $Status

        if ($Status -notin $ValidStatuses) {
            $Inconsistencies += "Status invalido para tarefa ${Id}: '$Status'"
            Write-Host "   [INVALIDO] ${Id}: status '$Status'" -ForegroundColor Red
        }

        if ($Status -eq 'COMPLETED') {
            $CompletedCount++
            $CompletedIds[$Id] = $true
            if ($Detailed) { Write-Host "   [COMPLETED] ${Id}: $Title" -ForegroundColor Green }
        } else {
            $PendingCount++
            Write-Host "   [$Status]   ${Id}: $Title" -ForegroundColor Yellow
        }
    }
    Write-Host "   Total de tarefas: $($TaskMatches.Count) ($CompletedCount concluidas, $PendingCount pendentes)" -ForegroundColor White

    # Validacao de dependencias
    $DepMatches = [regex]::Matches($TasksContent, '-\s+id:\s*"([^"]+)".*?dependencies:\s*\[(.*?)\]', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    foreach ($DepMatch in $DepMatches) {
        $TaskId = $DepMatch.Groups[1].Value.Trim()
        $RawDeps = $DepMatch.Groups[2].Value.Trim()
        if (-not [string]::IsNullOrWhiteSpace($RawDeps)) {
            $DepList = $RawDeps -split ',' | ForEach-Object { $_.Trim().Trim('"').Trim("'") }
            foreach ($Dep in $DepList) {
                if (-not $AllIds.ContainsKey($Dep)) {
                    $Inconsistencies += "Tarefa $TaskId depende de tarefa inexistente: $Dep"
                    Write-Host "   [FALHA] $TaskId depende de $Dep que nao existe no grafo" -ForegroundColor Red
                } elseif ($AllIds[$TaskId] -eq 'COMPLETED' -and -not $CompletedIds.ContainsKey($Dep)) {
                    $Inconsistencies += "Inconsistencia de grafo: tarefa $TaskId esta COMPLETED mas sua dependencia $Dep nao esta concluida!"
                    Write-Host "   [FALHA] $TaskId COMPLETED com dependencia pendente: $Dep" -ForegroundColor Red
                }
            }
        }
    }
}

# ---------------------------------------------------------
# 7. Verificacao de Skills Oficiais em .agents/skills/
# ---------------------------------------------------------
Write-Host "`n>> 7. Auditoria de Skills (.agents/skills/):" -ForegroundColor Cyan
$RequiredSkills = @(
    'cardcontrol-verify',
    'agent-state-sync',
    'room-db-migration',
    'bank-notification-parser',
    'financial-math-audit',
    'ponytail',
    'context-mode',
    'graphify',
    'humanize'
)

foreach ($Skill in $RequiredSkills) {
    $SkillManifest = ".agents\skills\$Skill\SKILL.md"
    if (-not (Test-Path $SkillManifest)) {
        $Inconsistencies += "Skill obrigatoria ausente ou sem SKILL.md: $Skill"
        Write-Host "   [FALHA] Ausente: $Skill ($SkillManifest)" -ForegroundColor Red
    } else {
        $SkillText = Get-Content $SkillManifest -Raw
        if ($SkillText -match '^---\s*[\r\n]+name:\s*([^\r\n]+)[\r\n]+description:\s*([^\r\n]+)') {
            Write-Host "   [OK] $Skill (manifesto integro)" -ForegroundColor Green
        } else {
            $Inconsistencies += "Skill $Skill possui SKILL.md sem YAML frontmatter valido (name/description)"
            Write-Host "   [FALHA] ${Skill}: YAML frontmatter invalido" -ForegroundColor Red
        }
    }
}

# ---------------------------------------------------------
# 8. Deteccao de Referencias Residuais (.agent/)
# ---------------------------------------------------------
Write-Host "`n>> 8. Prevencao de Referencias Residuais (.agent/):" -ForegroundColor Cyan
$FilesToCheckResiduals = @(
    '.agents\CARDCONTROL_STATE.md',
    '.agents\CARDCONTROL_TASKS.yaml',
    '.agents\CARDCONTROL_EXECPLAN.md',
    '.agents\CARDCONTROL_DECISIONS.md',
    '.agents\skills\agent-state-sync\SKILL.md',
    'README.md',
    'AGENTS.md'
)

foreach ($CheckFile in $FilesToCheckResiduals) {
    if (Test-Path $CheckFile) {
        $Content = Get-Content $CheckFile -Raw
        # Procura '.agent/' isolado que nao seja '.agents/'
        if ($Content -match '(?<!\.agents)(?<!\.agents/)\.agent/') {
            $Inconsistencies += "Referencia residual legada '.agent/' encontrada em: $CheckFile"
            Write-Host "   [FALHA] Referencia legada .agent/ encontrada em $CheckFile" -ForegroundColor Red
        } else {
            Write-Host "   [OK] Sem referencias residuais em $CheckFile" -ForegroundColor Green
        }
    }
}

# ---------------------------------------------------------
# 9. Guardrails de Assinatura de Release
# ---------------------------------------------------------
Write-Host "`n>> 9. Guardrails de Assinatura de Release:" -ForegroundColor Cyan
if (Test-Path '.github\workflows\release.yml') {
    $ReleaseWorkflow = Get-Content '.github\workflows\release.yml' -Raw
    $SecretsToCheck = @(
        'CARDCONTROL_KEYSTORE_BASE64',
        'CARDCONTROL_KEYSTORE_PASSWORD',
        'CARDCONTROL_KEY_ALIAS',
        'CARDCONTROL_KEY_PASSWORD'
    )
    $MissingSecrets = @()
    foreach ($Sec in $SecretsToCheck) {
        if ($ReleaseWorkflow -notmatch $Sec) {
            $MissingSecrets += $Sec
        }
    }
    if ($MissingSecrets.Count -gt 0) {
        $Inconsistencies += "Workflow de release nao valida os segredos: $($MissingSecrets -join ', ')"
        Write-Host "   [FALHA] Segredos ausentes no workflow: $($MissingSecrets -join ', ')" -ForegroundColor Red
    } else {
        Write-Host '   [OK] Guardrails de segredos presentes em .github/workflows/release.yml' -ForegroundColor Green
    }
}

# ---------------------------------------------------------
# Resumo Final e Codigo de Saida
# ---------------------------------------------------------
Write-Host "`n==========================================================" -ForegroundColor Cyan
if ($Inconsistencies.Count -eq 0) {
    Write-Host " AUDITORIA CONCLUIDA: 100% INTEGRO E SINCRONIZADO!        " -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Cyan
    exit 0
} else {
    Write-Host " AUDITORIA FALHOU: $($Inconsistencies.Count) INCONSISTENCIA(S) DETECTADA(S):" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Cyan
    foreach ($Err in $Inconsistencies) {
        Write-Host " - $Err" -ForegroundColor Red
    }
    Write-Host ''
    exit 1
}
