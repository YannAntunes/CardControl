# CardControl — Ponto de Retomada e Estado Canônico

- Data da Última Auditoria: 16 de Outubro de 2026
- Branch ativa: `fix/persistent-release-signing`
- HEAD SHA: `f2e9145`
- Status do Projeto: PRs #7 e #13 integrados na main; assinatura persistente configurada e validada; release pronta para execução em Draft
- Versão Atual: `v2.3.0` (versionCode 5)
- Versão do banco Room: `v4` (`MIGRATION_3_4`)

---

## 1. Resumo Executivo das Entregas Validadas

1. **Harmonização de Bancos e Parser (PR #7 integrado via commit 7ff84ba):**
   - Unificação de parsers em `BankNotificationParser.kt` com suporte aos maiores bancos do Brasil.
   - `BankNotificationProcessor.kt` conectando captura, resolução segura, concorrência e deduplicação de 120s.
   - Enum canônico `SupportedBank` com sanitização, normalização sem acentos, tokenização e tratamento rigoroso de ambiguidade.
   - Migração Room v3 -> v4 com backfill inteligente retroativo e validação em SQLite real (100% PASS).
   - Suíte de 74 testes aprovada com louvor no CI do GitHub Actions (`Android CI`, `CodeQL`, `Dependency Review`).

2. **Gerenciamento Completo de Cartões e UX Liquid Glass (PR #13 integrado via commit 65d46a7):**
   - Edição e exclusão segura de cartões com diálogos modais dedicados e validação estrita.
   - Bloqueio de exclusão quando há histórico financeiro (transações, parcelas ou pagamentos).
   - Vinculação de pessoa/titular padrão e seletor visual de bancos no modal.
   - Suíte consolidada de 82 testes unitários aprovados com 100% de sucesso.

3. **Assinatura Persistente e Fail-Closed de Release (`release.yml`):**
   - Removido o fallback de geração de chave efêmera no runner.
   - A keystore local persistente (`%LOCALAPPDATA%\CardControlSigning\cardcontrol-release.jks`) e suas credenciais foram validadas.
   - Certificado SHA-256 verificado diretamente via `keytool`: `A5:24:7A:59:4D:30:C8:FA:F4:6C:8B:30:06:AB:09:27:35:CC:DE:FD:67:81:1B:41:96:5E:01:87:28:64:93:28`.
   - Os 4 Repository Secrets (`CARDCONTROL_KEYSTORE_BASE64`, `CARDCONTROL_KEYSTORE_PASSWORD`, `CARDCONTROL_KEY_ALIAS`, `CARDCONTROL_KEY_PASSWORD`) foram cadastrados e confirmados via GitHub CLI sem exibição de valores.
   - As 2 Repository Variables (`CARDCONTROL_CERT_SHA256` e `CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED=true`) foram cadastradas e confirmadas via GitHub CLI.
   - Workflow gera exclusivamente **Draft Release** para conferência humana prévia.

4. **Saneamento e Limpeza Estrita de Binários:**
   - Remoção de binários compilados fora do canal oficial de Releases do GitHub.
   - Atualização de `dist/README.md` e regras de integridade do `.gitignore`.

---

## 2. Evidências Empíricas dos Gates de Engenharia

- **Testes Unitários:** 82 testes executados, 82 aprovados, 0 falhas, 0 erros, 0 ignorados (`BUILD SUCCESSFUL`).
- **Validação em SQLite Real (`scripts/test_room_v3_to_v4_real_sqlite.py`):** 100% aprovado com tratamento de bancos ambíguos.
- **Canários de Empacotamento Seguro (`scripts/test_bundle_canary.py`):** 28 arquivos-canário sensíveis bloqueados com sucesso.
- **Compilação e Linting:** `lintDebug` aprovado com 0 erros, `assembleDebug` gerado com sucesso.
- **Integridade Git:** `git diff --check` aprovado sem inconsistências.
- **Configuração Remota:** GitHub CLI confirmou os 4 secrets e as 2 variables no repositório `kauankelvin7/CardControl`.

---

## 3. Inventário de Configuração Remota

| Item | Tipo | Valor / Status |
| :--- | :--- | :--- |
| `CARDCONTROL_KEYSTORE_BASE64` | Secret | Confirmado via GitHub API |
| `CARDCONTROL_KEYSTORE_PASSWORD` | Secret | Confirmado via GitHub API |
| `CARDCONTROL_KEY_ALIAS` | Secret | Confirmado via GitHub API |
| `CARDCONTROL_KEY_PASSWORD` | Secret | Confirmado via GitHub API |
| `CARDCONTROL_CERT_SHA256` | Variable | `A5:24:7A:59:4D:30:C8:FA:F4:6C:8B:30:06:AB:09:27:35:CC:DE:FD:67:81:1B:41:96:5E:01:87:28:64:93:28` |
| `CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED` | Variable | `true` |