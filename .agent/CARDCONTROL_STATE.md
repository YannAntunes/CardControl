# CardControl — Estado Atual do Repositório

Última atualização: 16/10/2026
Branch ativa: `feat/subscription-and-recurring-tracker` (Base: `feat/bank-notification-matcher-and-security` no commit `3a3cb91`)

## Ponto Exato de Retomada: Entrega do Detector de Assinaturas e Gastos Recorrentes (Subscription Tracker)

O repositório recebeu a funcionalidade 100% offline de detecção de assinaturas e despesas recorrentes:

1. **Schema Room v5 e Migração Segura:** Evolução do banco SQLite para a versão 5 (`5.json`) com `MIGRATION_4_5`, adicionando a coluna `isRecurring` na tabela `transactions` e criando a tabela `recurring_charges` com chaves estrangeiras e índices otimizados (`cardId`, `holderId`, `merchant`).
2. **Motor Determinístico de Detecção (`SubscriptionTracker.kt`):** Algoritmo que agrupa transações por estabelecimento normalizado e identifica recorrências mensais (janela de 28 a 32 dias) ou transações marcadas manualmente como recorrentes. Detecta automaticamente variações de preço ("price creep") e calcula custos fixos comprometidos e impacto percentual no limite total dos cartões.
3. **Integração no Repositório e ViewModel:** Métodos para sincronização automática (`syncRecurringCharges`), controle de ativação/pausa de assinaturas (`setRecurringChargeActive`) e sinalização manual de transações (`markTransactionRecurring`), com reatividade no `DashboardUiState`.
4. **Interface Visual LiquidGlass:** Componente `RecurringChargesCard` no dashboard com resumo de custos fixos, porcentagem de limite, badges de alerta de aumento e alternador ativo/pausado, além de ação rápida nas parcelas para marcar compras como recorrentes.
5. **Cobertura Automatizada Completa:** Testes de migração (`RoomV4ToV5MigrationTest.kt`), testes unitários de detecção e cálculos (`SubscriptionTrackerTest.kt`) e validações de ciclo de vida no harness (`SecurityAndDomainTestHarness.kt`).

---

## Inventário de Módulos e Componentes

| Módulo / Arquivo | Responsabilidade | Status de Validação |
| :--- | :--- | :--- |
| `app/build.gradle.kts` | Configurações de compilação Android, versionCode 4, versionName 2.2.0 | Validado |
| `SubscriptionTracker.kt` | Motor determinístico de detecção de recorrências, price creep e cálculos de limite | 100% testado (`SubscriptionTrackerTest`) |
| `RoomV4ToV5MigrationTest.kt` | Teste de migração Room v4 -> v5 e validação de schema SQL | Testado e aprovado |
| `AppDatabase.kt` / `AppDao.kt` | Banco SQLite Room v5 com `MIGRATION_4_5` e consultas de recorrências | Schema `5.json` exportado e validado |
| `Entities.kt` | `TransactionEntity.isRecurring` e `RecurringChargeEntity` | Validado no KSP Room v5 |
| `CardRepository.kt` | Sincronização de despesas recorrentes e Mutex atômico | Concorrência e sincronização testadas |
| `MainViewModel.kt` | Exposição de fluxos de assinaturas e custos no `DashboardUiState` | Validado |
| `LiquidGlass.kt` / `MainScreen.kt` | `RecurringChargesCard` e ações rápidas com tema Obsidian/DeepSlate | Validado visual e estruturalmente |
| `scripts/test_bundle_canary.py` | Teste de 28 arquivos-canário sensíveis | **100% PASS** (28/28) |
| `MoneyUtils.kt` | Parser de strings BRL para centavos `Long` e formatação monetária | Testado no Harness |
| `ReportEngine.kt` | Faturas em PDF, mensagens WhatsApp e e-mails SMTP seguros | Centavos `Long`; sanitização ativa |
| `BankNotificationParser.kt` | Extração de compras em centavos e deduplicação SHA-256 | Regex endurecida contra ReDoS |

---

## Evidências dos Gates de Engenharia

- **Testes Unitários:**
  `.\gradlew.bat testDebugUnitTest` -> **SUCESSO:** 78/78 testes unitários aprovados (incluindo testes de cadência, price creep, migração e concorrência).
- **Lint e Compilação de Debug:**
  `.\gradlew.bat lintDebug assembleDebug` -> **SUCESSO:** APK compilado, lint sem erros e schema `5.json` exportado.
- **Teste de Canários de Segurança:**
  `python scripts/test_bundle_canary.py` -> **SUCESSO:** 28/28 arquivos-canário sensíveis bloqueados.
- **Verificação de Diff e Whitespace:**
  `git diff --check` -> **SUCESSO:** Zero erros de formatação ou whitespace.

