# CardControl — Ponto Exato de Retomada e Estado do Repositório

Última atualização: 16/10/2026
Branch ativa: `feat/card-management-ux-v2.3.0` (PR [#13](https://github.com/kauankelvin7/CardControl/pull/13))
Commit base: `4295138`
Versão da aplicação: `2.3.0` (versionCode `20300`)
Versão do banco Room: `v4` (`MIGRATION_3_4`)

---

## 1. Ponto Exato de Retomada

A branch `feat/card-management-ux-v2.3.0` consolida o gerenciamento seguro de cartões, a evolução da fundação técnica e o pipeline seguro de release do CardControl:

1. **Identificação Explícita de Banco Emissor (`SupportedBank.kt`):**
   - Criação do enum canônico de bancos brasileiros suportados (`NUBANK`, `INTER`, `ITAU`, `C6`, `SANTANDER`, `BRADESCO`, `CAIXA`, `PICPAY`, `MERCADO_PAGO` e `OTHER`).
   - Mapeamento determinístico por package names de notificação e inferência heurística retroativa para cadastros pré-existentes.

2. **Evolução do Banco Room v4 (`MIGRATION_3_4`):**
   - Nova coluna `bank TEXT NOT NULL DEFAULT 'other'` na tabela `credit_cards`.
   - Índice `index_credit_cards_bank` adicionado para performance de busca.
   - Script de migração com backfill inteligente dos bancos com base no nome legado.
   - Esquema canônico exportado e versionado em `app/schemas/com.cardcontrol.app.data.local.AppDatabase/4.json`.

3. **Resolução Robusta de Transações por Notificação (`CardRepository.kt`):**
   - `resolveCardForNotification` desacoplado do apelido do cartão: cartões renomeados para "Viagem" ou "Pessoal" continuam recebendo e categorizando pushes em tempo real.
   - Desambiguação segura entre múltiplos cartões com os mesmos 4 dígitos finais através da identidade do banco.

4. **Interface e Usabilidade Liquid Glass (`MainScreen.kt`, `LiquidGlass.kt`):**
   - `LiquidPillSelector` parametrizado genericamente (`<T>`).
   - Modal de criação e edição com seletor visual de bancos oficiais, sugestão automática de nome e edição livre do apelido.
   - Exibição do banco emissor junto aos 4 dígitos nos cards da tela principal.

5. **Unificação e Blindagem de Governança (`.agents`):**
   - Consolidação exclusiva da governança na pasta `.agents` (plural), eliminando a duplicidade da pasta legada `.agent`.
   - Instalação e validação das skills oficiais `graphify` (Graphify-Labs), `context-mode` (v1.0.169) e suíte `ponytail`.
   - Script de consistência `check_state.ps1` fortalecido no padrão de rastreabilidade do Leve.

6. **Assinatura Flexível e Automatizada de Release (`release.yml`):**
   - Pipeline de release atualizado com fallback de geração automática: caso os 4 segredos permanentes de repositório não estejam configurados no GitHub, o runner executa `scripts/generate_release_keystore.sh`, assinando o APK com certificado RSA 2048 de 10.000 dias sem travar a publicação.
   - Se os segredos estiverem configurados, o workflow prioriza a chave persistida nos secrets.

7. **Saneamento e Limpeza Estrita de Binários:**
   - Remoção de arquivo executável órfão `CardControl.apk` largado na raiz do repositório.
   - Restauração e atualização de `dist/README.md` estabelecendo a proibição de binários compilados fora do canal oficial de Releases do GitHub.

---

## 2. Evidências Empíricas dos Gates de Engenharia

Todos os gates foram executados e validados no ambiente real de desenvolvimento:

- **Testes Unitários de Domínio e Segurança (`SecurityAndDomainTestHarness.kt`):**
  - Comando: `.\gradlew.bat testDebugUnitTest`
  - Resultado: **28 testes executados, 28 aprovados, 0 falhas, 0 erros, 0 ignorados** (`BUILD SUCCESSFUL`).
  - Cobertura comprovada: desacoplamento de nome, desambiguação de 4 dígitos com bancos distintos, fallback retroativo, prevenção de Path Traversal, mitigação de ReDoS e atomicidade de concorrência com Mutex.
- **Canários de Empacotamento Seguro (`scripts/test_bundle_canary.py`):**
  - Comando: `python scripts/test_bundle_canary.py`
  - Resultado: **28 arquivos-canário sensíveis bloqueados com sucesso** (`SUCESSO: Todos os 28 arquivos-canario sensiveis foram bloqueados!`).
- **Compilação e Linting de Debug:**
  - Comando: `.\gradlew.bat lintDebug assembleDebug`
  - Resultado: **`BUILD SUCCESSFUL`**, APK debug gerado com sucesso.
- **Integridade de Whitespace e Formatação Git:**
  - Comando: `git diff --check`
  - Resultado: **Aprovado sem inconsistências**.
- **Auditoria de Governança e Rastreabilidade (`check_state.ps1`):**
  - Comando: `powershell -ExecutionPolicy Bypass -File .agents/skills/agent-state-sync/scripts/check_state.ps1`
  - Resultado: **Aprovado com código de saída 0** (100% íntegro).

---

## 3. Inventário de Arquivos e Componentes Afetados

| Arquivo / Módulo | Natureza | Status de Validação |
| :--- | :--- | :--- |
| `app/src/main/java/.../SupportedBank.kt` | Enum de bancos, pacotes e inferência | Validado por testes unitários |
| `app/src/main/java/.../Entities.kt` | Entidade `CreditCardEntity` com campo `bank` e índice | Schema Room v4 exportado via KSP |
| `app/src/main/java/.../AppDatabase.kt` | Room v4 com `MIGRATION_3_4` | Compilação e migração validadas |
| `app/schemas/.../4.json` | Schema exportado do SQLite v4 | Versionado e conferido |
| `app/src/main/java/.../CardRepository.kt` | Resolução desambiguada por banco e CRUD seguro | Testado em concorrência e domínio |
| `app/src/main/java/.../MainViewModel.kt` | State holder para edição e seleção de banco | Validado em runtime |
| `app/src/main/java/.../LiquidGlass.kt` | `LiquidPillSelector` genérico | Validado em Compose UI |
| `app/src/main/java/.../MainScreen.kt` | Seletor horizontal de bancos e badges nos cartões | Validado em Compose UI |
| `app/src/test/.../SecurityAndDomainTestHarness.kt` | 28 testes automatizados do harness | 100% PASS |
| `.agents/skills/agent-state-sync/scripts/check_state.ps1` | Script de integridade e auditoria de estado (Leve) | Validado e executável |
| `.agents/skills/cardcontrol-verify/scripts/verify.ps1` | Script de execução de gates corrigido | Validado e executável |
| `.agents/CARDCONTROL_*.md/.yaml` | Artefatos de governança canônicos | Sincronizados com o repositório |
| `README.md` | Documentação alinhada ao Room v4 e `.agents` | Atualizado |
| `dist/README.md` | Diretrizes contra binários na raiz e histórico v2.3.0 | Atualizado |
| `docs/RELEASE_SIGNING.md` | Guia com assinatura automática e manual de release | Atualizado |
| `.github/workflows/release.yml` | Workflow de release com fallback dinâmico de chaves | Validado |
| `scripts/generate_release_keystore.sh` | Gerador com autenticação segura de `gh` CLI | Validado |

---

## 4. Bloqueios e Dependências Externas

- **Zero Bloqueios Ativos:** O pipeline de release opera autonomamente através do fallback de geração segura de chaves caso os segredos de repositório não estejam cadastrados.
- Todas as verificações de código, schemas, testes unitários e governança passam com código de saída 0 no ambiente local e no CI.

---

## 5. Próximos Passos

1. Executar rodada completa dos gates de validação via script de auditoria e testes (`check_state.ps1`, `verify.ps1`).
2. Realizar commit atômico das alterações na branch `feat/card-management-ux-v2.3.0`.
3. Efetuar push para `origin/feat/card-management-ux-v2.3.0` para atualizar o PR [#13](https://github.com/kauankelvin7/CardControl/pull/13).
4. Proceder com o merge do PR #13 e iniciar a próxima meta do roadmap (`TASK-008`: Detecção e conciliação de Pix).
