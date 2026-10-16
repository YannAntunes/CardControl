# CardControl — Estado Atual do Repositório

Última atualização: 16/10/2026
Branch ativa: `feat/release-readiness-v2.2.0` (Base: `main` no commit `001121b`)

## Ponto Exato de Retomada: Prontidão para Release Oficial v2.2.0

O repositório foi integralmente auditado, endurecido e preparado para publicação de nova release:

1. **Versionamento Oficial v2.2.0:** Atualização em `app/build.gradle.kts` para `versionCode = 4` e `versionName = "2.2.0"`, desvinculando formalmente a árvore do ciclo legado v2.1.0 revogado.
2. **Pipeline de Release Seguro com Workflow Dispatch:** Atualização de `.github/workflows/release.yml` para suportar tanto tags `v*` quanto disparos manuais controlados (`workflow_dispatch`), mantendo a checagem obrigatória de segredos e bloqueio de chaves commitadas.
3. **Provisionador Automatizado de Keystore:** Criação de `scripts/generate_release_keystore.ps1` e `scripts/generate_release_keystore.sh` para geração do par RSA 2048 bits com validade de 10.000 dias, extração de SHA-256 Fingerprint e provisionamento automático nos segredos do GitHub Actions via `gh secret set`.
4. **Proteção Rigorosa de Empacotamento:** `scripts/test_bundle_canary.py` expandido para 28 arquivos-canário sensíveis (incluindo `temp_keystore/`, `.p12`, `release-secrets.env`), com **100% PASS**.
5. **Documentação Alinhada e Transparente:**
   - `README.md`: Atualizado com status de segurança da versão v2.2.0, remoção de avisos de suspensão, detalhamento da arquitetura de centavos `Long` e guia de compilação.
   - `SECURITY.md`: Resolução formal do incidente da chave legada e documentação dos controles ativos (CRLF, Path Traversal, ReDoS, Mutex de concorrência e segredos de release).
   - `dist/README.md`: Histórico de integridade registrando a revogação da v2.1.0 e canal oficial via GitHub Releases.
   - `docs/RELEASE_SIGNING.md`: Procedimento operacional passo a passo para geração de chaves e publicação.

---

## Inventário de Módulos e Componentes

| Módulo / Arquivo | Responsabilidade | Status de Validação |
| :--- | :--- | :--- |
| `app/build.gradle.kts` | Configurações de compilação Android, versionCode 4, versionName 2.2.0 | Validado |
| `.github/workflows/release.yml` | Workflow de Release com guardrails de assinatura e suporte a workflow_dispatch | Validado |
| `scripts/generate_release_keystore.ps1` | Gerador automatizado de chave RSA e provisionamento de segredos via gh CLI | Testado e operacional |
| `scripts/generate_release_keystore.sh` | Versão Bash para ambientes Linux/macOS | Validado |
| `scripts/build_antigravity_bundle.py` | Empacotador de distribuição de código-fonte CardControl v2.2 | Atualizado com exclusões |
| `scripts/test_bundle_canary.py` | Teste de 28 arquivos-canário sensíveis | **100% PASS** (28/28) |
| `MoneyUtils.kt` | Parser de strings BRL para centavos `Long` e formatação monetária | Testado no Harness |
| `ReportEngine.kt` | Faturas em PDF, mensagens WhatsApp e e-mails SMTP seguros | Centavos `Long`; sanitização ativa |
| `BankNotificationParser.kt` | Extração de compras em centavos e deduplicação SHA-256 | Regex endurecida contra ReDoS |
| `CardRepository.kt` | Regra financeira, cálculo de corte, centavos e Mutex atômico | Concorrência e integridade validadas |
| `AppDatabase.kt` / `AppDao.kt` | Banco SQLite private (`card_control_offline.db`), Room v3 | Schemas versionados em `app/schemas/` |
| `LiquidGlass.kt` / `MainScreen.kt` | Design system com tema Obsidian, UI Compose e diálogos | Validado para centavos `Long` |

---

## Evidências dos Gates de Engenharia

- **Teste de Canários de Segurança:**
  `python scripts/test_bundle_canary.py` -> **SUCESSO:** Todos os 28 arquivos-canário sensíveis bloqueados.
- **Verificação de Diff e Whitespace:**
  `git diff --check` -> **SUCESSO:** Zero erros de formatação ou whitespace.
- **Teste de Empacotamento de Bundle:**
  `python scripts/build_antigravity_bundle.py` -> **SUCESSO:** Bundle `CardControl_v2_2.zip` gerado sem vazar chaves.
- **Auditoria de Governança:**
  `powershell -ExecutionPolicy Bypass -File .agents/skills/agent-state-sync/scripts/check_state.ps1` -> **SUCESSO:** Sincronizado.
