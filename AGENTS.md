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

### B. Subagente Implementador (`.agents/agents/implementador.md`)
- **Papel:** Especialista em desenvolvimento Android nativo (Kotlin, Jetpack Compose, Room SQLite offline-first).
- **Responsabilidades:**
  - Receber tarefas delimitadas com critérios verificáveis.
  - Trabalhar em branch ou worktree isolada quando suportado.
  - Preservar alterações preexistentes e não modificar arquivos fora do escopo sem justificativa técnica.
  - Atacar a causa raiz dos problemas, evitando máscaras ou fallbacks inseguros.
  - Criar e atualizar testes de regressão automatizados no `SecurityAndDomainTestHarness.kt`.
  - Registrar comandos executados e resultados reais de compilação e teste.
  - Não realizar commit, push, merge ou publicação por iniciativa própria.

### C. Subagente Auditor Independente (`.agents/agents/auditor.md`)
- **Papel:** Revisor técnico adversarial com contexto isolado.
- **Ferramentas:** Estritamente limitadas a **somente-leitura** e execução de testes/diagnósticos (`view_file`, `list_dir`, `grep_search`, `run_command`, `read_url_content`, `search_web`), sem permissão de escrita ou edição no código.
- **Responsabilidades:**
  - Iniciar a revisão em contexto separado após a implementação.
  - Inspecionar diff real (`git diff`, `git diff --stat origin/main`) e código adjacente.
  - Buscar bugs, regressões, falhas de segurança (CRLF, Path Traversal, ReDoS), concorrência e integridade financeira (centavos `Long`).
  - Verificar se os testes cobrem cenários reais ou apenas asserções tautológicas/mocks.
  - Separar rigorosamente fatos comprovados de hipóteses.
  - Reportar cada achado com severidade, arquivo, localização, evidência e recomendação.
  - Emitir parecer fundamentado: Aprovado, Aprovado com Ressalvas ou Bloqueado.

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
