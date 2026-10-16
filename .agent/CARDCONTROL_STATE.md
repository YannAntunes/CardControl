# CardControl — Estado Atual do Repositório

Última atualização: 15/10/2026
Branch de desenvolvimento ativa: `feat/hardening-lote4` (Commit base: `8928672`)

## Ponto Exato de Retomada: Lote 5.2 de Hardening e Auditoria Autônoma

O commit `8928672` (`wip: checkpoint CardControl hardening lote 5.2`) e o ciclo autônomo atual consolidaram as seguintes fundações de engenharia e segurança:
1. **Representação Canônica em Centavos (`Long`):** Migração de todas as grandezas monetárias (`Double` -> `Long` centavos) no Room v3 e no domínio, evitando imprecisão de ponto flutuante.
2. **Room SQLite v3 & Exportação de Schemas:** Implementação de `MIGRATION_2_3` com preservação de integridade referencial, 11 índices SQLite para foreign keys e exportação de esquemas via KSP em `app/schemas/`.
3. **Atomicidade e Concorrência:** Introdução do `CardRepository.expenseMutex` estático compartilhado em `companion object` para prevenir duplicações em corridas críticas multi-instância na mesma JVM.
4. **Governança por Quatro Subagentes Especializados:** Formalização da orquestração com o Antigravity delegando tarefas delimitadas em `.agents/agents/`:
   - Agente 1 — Auditor Técnico e Segurança (`auditor_seguranca.md`)
   - Agente 2 — Implementador Android (`implementador_android.md`)
   - Agente 3 — Front-end / UI/UX Android (`frontend_ui_ux.md`)
   - Agente 4 — QA e Revisor Independente (`qa_revisor.md`)
5. **Ferramentas de Engenharia Integradas:**
   - **Humanize:** Instalado em `.agents/skills/humanize/` para revisão e clareza de linguagem sem alterações cosméticas fúteis.
   - **Ponytail:** Instalado em `.agents/rules/ponytail.md` e `.agents/skills/ponytail*/` aplicando simplicidade, YAGNI e reutilização sem cortar validações ou segurança.
   - **Context Mode:** Instalado via npm com licença Elastic 2.0 (ELv2), verificado com `context-mode doctor` (PASS), com MCP server configurado em `mcp_config.json` e regras em `.agents/rules/context-mode.md`.
6. **Empacotamento Seguro & Teste de Canários:** Refatoração de `build_antigravity_bundle.py` com exclusão comprovada de 24 arquivos-canário sensíveis via `test_bundle_canary.py`.
7. **Harness de Testes Expandido:** Suíte `SecurityAndDomainTestHarness.kt` com 555 linhas cobrindo ataques de injeção, deduplicação sob concorrência, bordas de datas e parsing resiliente.

---

## Inventário de Módulos e Componentes

| Módulo / Arquivo | Responsabilidade | Status de Validação |
| :--- | :--- | :--- |
| `MoneyUtils.kt` | Parser de strings BRL para centavos `Long` e formatação monetária com locale pt-BR. | Coberto por testes unitários no Harness |
| `ReportEngine.kt` | Geração de faturas em PDF paginadas, mensagens WhatsApp formatadas e e-mails SMTP seguros. | Migrado para centavos `Long`; sanitização ativa |
| `BankNotificationParser.kt` | Extração de compras em centavos, estabelecimento, cartão e parcelas. Deduplicação SHA-256. | Regex endurecida contra falsos positivos e ReDoS |
| `BankNotificationListener.kt` | Serviço em segundo plano com escuta de notificações. Dispara lançamentos e e-mails. | Integrado com resolução atômica de cartões |
| `SecuritySanitizer.kt` | Defesa contra CRLF em SMTP, Path Traversal em relatórios PDF e controle de ReDoS. | Validado com casos de injeção direta |
| `CardRepository.kt` | Regra financeira, cálculo de corte (`closingDay`), rateio em centavos e concorrência com Mutex. | Concorrência e deduplicação validadas |
| `AppDatabase.kt` / `AppDao.kt` | Banco SQLite private (`card_control_offline.db`), Room v3 com `MIGRATION_2_3` e schemas KSP. | Schemas versionados em `app/schemas/` |
| `CredentialStore.kt` | Cofre de credenciais SMTP com `EncryptedSharedPreferences` via Android Keystore. | Integrado anteriormente sem segredos em disco |
| `LiquidGlass.kt` / `MainScreen.kt` | Design system com tema Obsidian, UI Compose e diálogos com teclados dedicados. | Interface adaptada para centavos `Long` |
| `SecurityAndDomainTestHarness.kt` | Suíte com 555 linhas de asserções cobrindo segurança, concorrência e integridade contábil. | Cobertura ampliada para cenários adversariais |
| `scripts/test_bundle_canary.py` | Teste automatizado de bloqueio estrito de arquivos sensíveis durante o empacotamento. | 100% PASS (24 canários bloqueados) |
| `.agents/agents/` | Configurações dos 4 subagentes especializados (`auditor_seguranca`, `implementador_android`, `frontend_ui_ux`, `qa_revisor`). | Estruturados e documentados |
| `.agents/skills/` | Agent Skills modulares para validação, parsing, migrações, governança, matemática, humanize e ponytail. | Operacionais e testadas |

---

## Evidências dos Gates de Engenharia

- **Teste de Canários de Segurança:**
  `python scripts/test_bundle_canary.py` -> **SUCESSO:** Todos os 24 arquivos-canário sensíveis bloqueados.
- **Verificação de Diff e Whitespace:**
  `git diff --check` -> **SUCESSO:** Zero erros de sintaxe ou whitespace.
- **Empacotamento de Bundle:**
  `python scripts/build_antigravity_bundle.py` -> **SUCESSO:** Bundle gerado sem vazar chaves ou diretórios de build.
- **Auditoria de Governança:**
  `powershell -ExecutionPolicy Bypass -File .agents/skills/agent-state-sync/scripts/check_state.ps1` -> **SUCESSO:** Tarefas e arquivos essenciais sincronizados.
- **Diagnóstico do Context Mode:**
  `context-mode doctor` -> **SUCESSO:** FTS5 / SQLite nativo operacional, runtimes Bun/Python/pwsh detectados.
- **Validação Remota da CI (GitHub Actions):**
  Run ID `38787332271` no commit `8928672` aprovado com sucesso em todos os jobs (`Android Lint`, `Build Debug APK`, `Unit tests`).

---

## Bloqueio Externo e Próximos Passos

1. **Bloqueio de Release em Produção (Esperado):** Rotação da chave de release privada e configuração dos secrets no repositório GitHub (`CARDCONTROL_KEYSTORE_BASE64`, etc.) dependem de ação manual do mantenedor nas configurações confidenciais do repositório. O build de release está intencionalmente bloqueado para prevenir qualquer publicação insegura.
2. **Homologação Local de Compilação Android SDK:** A compilação local com Gradle requer a instalação do Android SDK (API 34) no host Windows; a integridade do código e a geração do APK de debug estão comprovadas pelo pipeline de CI do GitHub Actions.
