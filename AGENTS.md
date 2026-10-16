# CardControl — Instruções para Agentes e Orquestração

## 1. Arquitetura de Agentes (Orquestrador e Subagentes Especializados)

O projeto CardControl adota o **Antigravity** como agente principal e orquestrador de engenharia de software, operando com o modelo **Gemini 3.8 Flash** disponível no ambiente e delegando trabalho a subagentes especializados configurados em `.agents/agents/`:

### A. Antigravity (Orquestrador e Auditor Principal)
- **Papel:** Líder técnico do projeto e autoridade final de aprovação.
- **Responsabilidades:**
  - Levantar o estado real do repositório antes de cada tarefa (branches, commits, working tree, testes).
  - Definir escopo estrito, critérios de aceitação mensuráveis e avaliação de riscos.
  - Delegar implementação técnica ao subagente `implementador`.
  - Solicitar auditoria independente ao subagente `auditor` após a conclusão da implementação.
  - Analisar achados de auditoria, exigir correções na causa raiz e executar validações finais.
  - Recomendar ou autorizar integração somente com evidências reais comprovadas.
  - **Regra de Ouro:** Não aceita o relatório do implementador como prova; confere arquivos, diffs, logs e saídas reais do terminal.

### B. Agente 1 — Auditor Técnico e Segurança (`.agents/agents/auditor_seguranca.md` / `auditor.md`)
- **Papel:** Auditor técnico adversarial com ferramentas estritamente limitadas a **somente-leitura** e testes/diagnósticos (`view_file`, `list_dir`, `grep_search`, `run_command`, `read_url_content`, `search_web`).
- **Responsabilidades:**
  - Analisar arquitetura, persistência Room SQLite privada (`card_control_offline.db`), funcionamento 100% offline e dependências.
  - Auditar concorrência estática (`CardRepository.expenseMutex`), integridade contábil em centavos (`Long`) e defesas contra CRLF, Path Traversal e ReDoS.
  - Verificar integridade de CI/CD, pinagem de workflows por SHA e prevenção contra exposição de credenciais/keystores.
  - Separar rigorosamente fatos comprovados de hipóteses e reportar achados com severidade e recomendação na causa raiz.

### C. Agente 2 — Implementador Android (`.agents/agents/implementador_android.md` / `implementador.md`)
- **Papel:** Especialista em desenvolvimento Android nativo (Kotlin 1.9.24, Room 2.6.1 SQLite, Android Keystore).
- **Responsabilidades:**
  - Receber tarefas delimitadas com critérios verificáveis e atuar na causa raiz dos problemas.
  - Preservar a arquitetura offline-first e o modelo canônico de centavos inteiros (`Long`).
  - Manter migrações de banco seguras (`MIGRATION_2_3`) e schemas KSP exportados em `app/schemas/`.
  - Criar e atualizar testes de regressão automatizados no `SecurityAndDomainTestHarness.kt`.
  - Não realizar commit, push ou merge sem autorização do coordenador.

### D. Agente 3 — Front-end / UI/UX Android (`.agents/agents/frontend_ui_ux.md`)
- **Papel:** Especialista em interface Android Jetpack Compose e experiência do usuário.
- **Responsabilidades:**
  - Atuar sempre que correções ou melhorias demandarem ajustes visuais.
  - Reutilizar o design system oficial `LiquidGlass.kt` (tema Obsidian/DeepSlate, GlassTextField, LiquidPillSelector), evitando dependências externas desnecessárias.
  - Garantir estados de carregamento, erro, vazio, diálogos com teclados dedicados e acessibilidade (contrastes, contentDescription).
  - Aplicar o princípio de linguagem natural e clara do **Humanize** e o minimalismo/YAGNI do **Ponytail**.

### E. Agente 4 — QA e Revisor Independente (`.agents/agents/qa_revisor.md`)
- **Papel:** Validador adversarial de qualidade e aprovação.
- **Responsabilidades:**
  - Executar e auditar testes automatizados (`testDebugUnitTest`, `lintDebug`, `assembleDebug` e `test_bundle_canary.py`).
  - Inspecionar o diff completo (`git diff --stat origin/main`) em busca de regressões ou efeitos colaterais.
  - Emitir parecer fundamentado: **Aprovado**, **Aprovado com Ressalvas** ou **Rejeitado/Bloqueado**.

---

## 2. Fluxo Obrigatório de Governança em 9 Passos

Toda alteração técnica no CardControl deve obedecer estritamente à sequência:

1. **Levantamento de Estado:** Registrar branch atual, HEAD, working tree (`git status --short`) e fontes de verdade.
2. **Definição de Escopo:** Elaborar plano detalhado com critérios de aceitação e riscos mapeados.
3. **Delegação da Implementação:** Acionar o subagente `implementador` com escopo delimitado.
4. **Inspeção Intermediária:** O orquestrador examina o diff gerado e os testes executados.
5. **Delegação da Auditoria:** Acionar o subagente `auditor` em contexto independente.
6. **Remediação de Achados:** Corrigir os problemas relevantes apontados e solicitar revalidação.
7. **Validações Finais:** Executar a suíte de testes e compilação do Gradle no estado final consolidado.
8. **Rastreabilidade de SHA:** Confirmar que o SHA / estado validado corresponde ao código revisado.
9. **Entrega de Relatório:** Apresentar relatório final objetivo com evidências, pendências e parecer de integração.

---

## 3. Fontes de Verdade

Consulte os documentos nesta ordem de prioridade:

1. Pedido mais recente do usuário.
2. `.agent/CARDCONTROL_STATE.md` (estado atual comprovado e ponto exato de retomada).
3. `.agent/CARDCONTROL_TASKS.yaml` (grafo de tarefas e dependências).
4. `.agent/CARDCONTROL_EXECPLAN.md` (plano executável e marcos de engenharia).
5. `.agent/CARDCONTROL_DECISIONS.md` (decisões arquiteturais persistidas).
6. `README.md` e `SECURITY.md`.

Se houver divergência técnica, prevalece o código verificado no repositório e a saída real do compilador/testes sobre documentações históricas.

---

## 4. Estado Atual do Sistema e Contratos

- **Arquitetura:** Aplicativo Android nativo (Kotlin 1.9.24 + Jetpack Compose + Room 2.6.1 SQLite + Android Keystore), 100% offline-first.
- **Banco de Dados Room v3:** Banco SQLite privado `card_control_offline.db`. Todas as grandezas monetárias (`subLimit`, `totalLimit`, `totalAmount`, `amount`, `amountPaid`) utilizam representação inteira canônica em centavos (`Long`). Esquemas Room exportados e versionados em `app/schemas/`.
- **Captura em Tempo Real:** `BankNotificationListener` monitorando notificações push locais dos 9 maiores bancos do Brasil. Deduplicação inteligente via hash SHA-256 e janela de 120s.
- **Concorrência Segura:** `CardRepository.expenseMutex` compartilhado em `companion object` garantindo inserção atômica e proteção contra duplicatas entre múltiplas instâncias na mesma JVM.
- **Segurança de Entrada:** Sanitização contra CRLF injection (`sanitizeHeaderField`), bloqueio de Path Traversal (`sanitizeFileNameSegment`), e limite de payload contra ReDoS (`boundNotificationInput`).
- **Assinatura e Empacotamento:** A chave de release legada foi revogada. O buildType `release` não possui chaves hardcoded e nunca herda assinatura de `debug`. O build `debug` utiliza a chave padrão de desenvolvimento do Android SDK.

---

## 5. Regras de Produto e Arquitetura

1. **Privacidade e Offline Estrito:**
   - Nenhum dado de transação, fatura, cartão ou titular pode ser enviado para servidores externos.
   - Custo R$ 0 de infraestrutura e zero dependência de nuvem paga.
   - O único tráfego de rede opcional é o envio direto de e-mail por SMTP TLS configurado pelo próprio usuário.
2. **Cofre e Credenciais:**
   - Senhas de e-mail armazenadas exclusivamente em `EncryptedSharedPreferences` com cifra `AES256-GCM` amarrada ao hardware do `MasterKey`.
   - Proibido registrar segredos, tokens ou senhas em logs ou commits.
3. **Integridade de Faturas e Datas:**
   - Compras realizadas no dia de fechamento (`closingDay`) ou após avançam para a fatura do mês seguinte.
   - Viradas de ano civil incrementam o ano e resetam o mês para 1.
4. **Compatibilidade Room:**
   - Migrações seguras preservando foreign keys e índices SQLite (11 índices no schema v3).

---

## 6. Gates de Validação Obrigatórios

Nenhuma funcionalidade pode ser dada como concluída sem execução e aprovação dos seguintes comandos:

- **Testes Unitários:**
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Java\jdk-17"; .\gradlew.bat testDebugUnitTest --console=plain "-Pkotlin.compiler.execution.strategy=in-process"
  ```
- **Lint e Compilação de Debug:**
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Java\jdk-17"; .\gradlew.bat lintDebug assembleDebug --console=plain --max-workers=2 "-Pkotlin.compiler.execution.strategy=in-process"
  ```
- **Verificação de Whitespace e Diff:**
  ```powershell
  git diff --check
  git status --short
  git diff --stat origin/main
  ```

---

## 7. Proibições Absolutas

- Não fazer commit, push, merge de PRs, criação de tags ou publicação sem autorização explícita.
- Não reintroduzir chaves criptográficas ou arquivos `.keystore` no Git.
- Não alucinar execuções de comandos nem declarar pronto o que não foi comprovado no terminal.

---

## 8. Agent Skills no Repositório

O repositório possui Agent Skills modulares em `.agents/skills/` (e `.agent/skills/`) para padronizar e acelerar as ações repetitivas de desenvolvimento e validação:

| Skill | Caminho | Finalidade |
| :--- | :--- | :--- |
| **`cardcontrol-verify`** | [SKILL.md](file:///c:/Users/kelvi/Desktop/CardControl/.agents/skills/cardcontrol-verify/SKILL.md) | Execução automatizada dos gates de teste (`testDebugUnitTest`) e build (`assembleDebug`), detecção inteligente de JDK e relatórios. |
| **`bank-notification-parser`** | [SKILL.md](file:///c:/Users/kelvi/Desktop/CardControl/.agents/skills/bank-notification-parser/SKILL.md) | Guia e pipeline de extração de notificações bancárias, deduplicação SHA-256 (120s), sanitização e testes no Harness. |
| **`room-db-migration`** | [SKILL.md](file:///c:/Users/kelvi/Desktop/CardControl/.agents/skills/room-db-migration/SKILL.md) | Evolução segura do banco offline SQLite (`card_control_offline.db`), migração v2->v3 (centavos `Long`), índices e schemas KSP. |
| **`agent-state-sync`** | [SKILL.md](file:///c:/Users/kelvi/Desktop/CardControl/.agents/skills/agent-state-sync/SKILL.md) | Sincronização e auditoria dos artefatos de governança (`CARDCONTROL_STATE.md`, `CARDCONTROL_TASKS.yaml`, `CARDCONTROL_EXECPLAN.md`). |
| **`financial-math-audit`** | [SKILL.md](file:///c:/Users/kelvi/Desktop/CardControl/.agents/skills/financial-math-audit/SKILL.md) | Regras matemáticas com representação canônica em centavos (`Long`), rateio exato de parcelas e virada de ano no fechamento. |

---

## 9. Ferramentas de Engenharia Integradas

O ambiente conta com ferramentas especializadas instaladas a partir de seus repositórios oficiais:

| Ferramenta | Repositório Oficial | Localização no Projeto / Ambiente | Finalidade e Diretrizes |
| :--- | :--- | :--- | :--- |
| **`Humanize`** | `https://github.com/ameenmo/humanize-skill` | `.agents/skills/humanize/` | Revisão de linguagem em diálogos, mensagens de validação, documentação e comentários. Preserva a precisão técnica e remove clichês ou jargões artificiais. |
| **`Ponytail`** | `https://github.com/DietrichGebert/ponytail` | `.agents/rules/ponytail.md` e `.agents/skills/ponytail*/` | Modo sênior pragmático: menor código que resolve o problema por completo. Foco em reutilização, simplicidade e YAGNI, sem cortar testes, tratamento de erros, concorrência ou segurança. |
| **`Context Mode`** | `https://github.com/mksglu/context-mode` | `.agents/rules/context-mode.md`, `.agents/skills/context-mode/`, `mcp_config.json` | Servidor MCP (`node cli.bundle.mjs`) e indexador FTS5. Reduz overhead de saídas extensas no contexto do modelo e gerencia logs e diagnósticos de forma delimitada. |
