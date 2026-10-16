# CardControl â€” Ponto de Retomada e Estado CanÃ´nico

- Data da Ãšltima Auditoria: 16 de Outubro de 2026
- Branch ativa: `main`
- HEAD SHA: `b98795d`
- Status do Projeto: PRs #7 e #13 integrados na main; assinatura persistente configurada e validada; release pronta para execuÃ§Ã£o em Draft
- VersÃ£o Atual: `v2.3.0` (versionCode 5)
- VersÃ£o do banco Room: `v4` (`MIGRATION_3_4`)

---

## 1. Resumo Executivo das Entregas Validadas

1. **HarmonizaÃ§Ã£o de Bancos e Parser (PR #7 integrado via commit 7ff84ba):**
   - UnificaÃ§Ã£o de parsers em `BankNotificationParser.kt` com suporte aos maiores bancos do Brasil.
   - `BankNotificationProcessor.kt` conectando captura, resoluÃ§Ã£o segura, concorrÃªncia e deduplicaÃ§Ã£o de 120s.
   - Enum canÃ´nico `SupportedBank` com sanitizaÃ§Ã£o, normalizaÃ§Ã£o sem acentos, tokenizaÃ§Ã£o e tratamento rigoroso de ambiguidade.
   - MigraÃ§Ã£o Room v3 -> v4 com backfill inteligente retroativo e validaÃ§Ã£o em SQLite real (100% PASS).
   - SuÃ­te de 74 testes aprovada com louvor no CI do GitHub Actions (`Android CI`, `CodeQL`, `Dependency Review`).

2. **Gerenciamento Completo de CartÃµes e UX Liquid Glass (PR #13 integrado via commit 65d46a7):**
   - EdiÃ§Ã£o e exclusÃ£o segura de cartÃµes com diÃ¡logos modais dedicados e validaÃ§Ã£o estrita.
   - Bloqueio de exclusÃ£o quando hÃ¡ histÃ³rico financeiro (transaÃ§Ãµes, parcelas ou pagamentos).
   - VinculaÃ§Ã£o de pessoa/titular padrÃ£o e seletor visual de bancos no modal.
   - SuÃ­te consolidada de 82 testes unitÃ¡rios aprovados com 100% de sucesso.

3. **Assinatura Persistente e Fail-Closed de Release (`release.yml`):**
   - Removido o fallback de geraÃ§Ã£o de chave efÃªmera no runner.
   - A keystore local persistente (`%LOCALAPPDATA%\CardControlSigning\cardcontrol-release.jks`) e suas credenciais foram validadas.
   - Certificado SHA-256 verificado diretamente via `keytool`: `A5:24:7A:59:4D:30:C8:FA:F4:6C:8B:30:06:AB:09:27:35:CC:DE:FD:67:81:1B:41:96:5E:01:87:28:64:93:28`.
   - Os 4 Repository Secrets (`CARDCONTROL_KEYSTORE_BASE64`, `CARDCONTROL_KEYSTORE_PASSWORD`, `CARDCONTROL_KEY_ALIAS`, `CARDCONTROL_KEY_PASSWORD`) foram cadastrados e confirmados via GitHub CLI sem exibiÃ§Ã£o de valores.
   - As 2 Repository Variables (`CARDCONTROL_CERT_SHA256` e `CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED=true`) foram cadastradas e confirmadas via GitHub CLI.
   - Workflow gera exclusivamente **Draft Release** para conferÃªncia humana prÃ©via.

4. **Saneamento e Limpeza Estrita de BinÃ¡rios:**
   - RemoÃ§Ã£o de binÃ¡rios compilados fora do canal oficial de Releases do GitHub.
   - AtualizaÃ§Ã£o de `dist/README.md` e regras de integridade do `.gitignore`.

---

## 2. EvidÃªncias EmpÃ­ricas dos Gates de Engenharia

- **Testes UnitÃ¡rios:** 82 testes executados, 82 aprovados, 0 falhas, 0 erros, 0 ignorados (`BUILD SUCCESSFUL`).
- **ValidaÃ§Ã£o em SQLite Real (`scripts/test_room_v3_to_v4_real_sqlite.py`):** 100% aprovado com tratamento de bancos ambÃ­guos.
- **CanÃ¡rios de Empacotamento Seguro (`scripts/test_bundle_canary.py`):** 28 arquivos-canÃ¡rio sensÃ­veis bloqueados com sucesso.
- **CompilaÃ§Ã£o e Linting:** `lintDebug` aprovado com 0 erros, `assembleDebug` gerado com sucesso.
- **Integridade Git:** `git diff --check` aprovado sem inconsistÃªncias.
- **ConfiguraÃ§Ã£o Remota:** GitHub CLI confirmou os 4 secrets e as 2 variables no repositÃ³rio `kauankelvin7/CardControl`.

---

## 3. InventÃ¡rio de ConfiguraÃ§Ã£o Remota

| Item | Tipo | Valor / Status |
| :--- | :--- | :--- |
| `CARDCONTROL_KEYSTORE_BASE64` | Secret | Confirmado via GitHub API |
| `CARDCONTROL_KEYSTORE_PASSWORD` | Secret | Confirmado via GitHub API |
| `CARDCONTROL_KEY_ALIAS` | Secret | Confirmado via GitHub API |
| `CARDCONTROL_KEY_PASSWORD` | Secret | Confirmado via GitHub API |
| `CARDCONTROL_CERT_SHA256` | Variable | `A5:24:7A:59:4D:30:C8:FA:F4:6C:8B:30:06:AB:09:27:35:CC:DE:FD:67:81:1B:41:96:5E:01:87:28:64:93:28` |
| `CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED` | Variable | `true` |