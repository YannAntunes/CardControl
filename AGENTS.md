# CardControl — Instruções para Agentes

## Fontes de Verdade

Antes de qualquer implementação, alteração de código, migração ou decisão de escopo, consulte os documentos nesta ordem de prioridade:

1. Pedido mais recente do usuário.
2. `.agent/CARDCONTROL_STATE.md` (estado atual comprovado e ponto exato de retomada).
3. `.agent/CARDCONTROL_TASKS.yaml` (grafo de tarefas e dependências).
4. `.agent/CARDCONTROL_EXECPLAN.md` (plano executável e marcos de engenharia).
5. `.agent/CARDCONTROL_DECISIONS.md` (decisões arquiteturais persistidas).
6. `README.md` e `SECURITY.md`.

Se houver divergência técnica, prevalece o código verificado no repositório e a saída real do compilador/testes sobre documentações históricas.

---

## Estado Atual do Sistema

- **Arquitetura:** Aplicativo Android nativo (Kotlin 1.9.24 + Jetpack Compose + Room 2.6.1 + Android Keystore), 100% offline-first.
- **Persistência Local:** Banco de dados SQLite privado `card_control_offline.db` sem servidores ou banco em nuvem.
- **Captura em Tempo Real:** `BankNotificationListener` (`NotificationListenerService`) monitorando eventos de push locais dos 9 maiores bancos/carteiras do Brasil (Nubank, Inter, Itaú, C6, Santander, Bradesco, Caixa, PicPay, Mercado Pago).
- **Segurança e Sanitização:** Sanitização de entradas contra CRLF injection (`sanitizeHeaderField`), bloqueio de Path Traversal em nomes de titulares (`sanitizeFileNameSegment`), limite de payload para prevenir ReDoS (`boundNotificationInput`).
- **Precisão Financeira:** Divisão de parcelas com `BigDecimal` (`RoundingMode.DOWN`), garantindo soma exata de centavos ao concentrar o resto na 1ª parcela (`splitInstallmentAmounts`).
- **Deduplicação Inteligente:** Hash SHA-256 do pacote, valor, estabelecimento, final do cartão e janela temporal de 2 minutos para descarte de notificações duplicadas emitidas pelo banco.
- **Identidade Visual:** Design System **Liquid Glass** com paleta Obsidian/DeepSlate, brilhos ciano/indigo/esmeralda, Adaptive Icons oficiais e formulários inteligentes com teclados especializados (numérico, decimal, telefone e e-mail).
- **Assinatura e Empacotamento:** A chave de release e as senhas anteriormente versionadas devem ser consideradas comprometidas. O build `debug` usa a chave padrão do Android. A assinatura de release está temporariamente desativada até haver uma nova chave e um plano de atualização compatível com instalações existentes. Não publique artefatos de release enquanto esse bloqueio estiver ativo.

---

## Regras de Produto e Arquitetura

1. **Privacidade e Offline Estrito:**
   - Nenhum dado de transação, fatura, cartão ou titular pode ser enviado para servidores externos.
   - O aplicativo funciona com custo R$ 0 de infraestrutura e zero dependência de serviços em nuvem pagos ou proprietários.
   - O único tráfego de rede opcional é o envio direto de e-mail por SMTP TLS configurado pelo próprio usuário no cofre Keystore.
2. **Cofre e Credenciais:**
   - Senhas de e-mail nunca são gravadas em texto plano; use exclusivamente `EncryptedSharedPreferences` com cifra `AES256-GCM` amarrada ao hardware do `MasterKey`.
   - Nunca registre segredos, tokens ou senhas em logs (`Logcat`, logs de build ou testes).
3. **Integridade de Faturas e Datas:**
   - O cálculo do primeiro mês da fatura obedece rigorosamente ao dia de fechamento (`closingDay`). Compras realizadas no dia de fechamento ou após avançam para o mês seguinte.
   - Viradas de ano (dezembro para janeiro) devem incrementar o ano civil e resetar o mês para 1.
4. **Respeito aos Contratos e Compatibilidade:**
   - Não quebre os esquemas de migração do Room sem justificativa técnica.
   - Ao adicionar campos nas entidades (`Entities.kt`), crie migrações seguras ou mantenha fallback destrutivo documentado explicitamente.

---

## Validação e Critérios de Aceite (Gates)

Nenhuma funcionalidade pode ser declarada concluída sem validação com comandos reais:

- **Testes Unitários:**
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Java\jdk-17"; .\gradlew.bat testDebugUnitTest --console=plain "-Pkotlin.compiler.execution.strategy=in-process"
  ```
  Todas as asserções da suíte `SecurityAndDomainTestHarness` devem passar com zero falhas e zero erros.

- **Compilação de Debug:**
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Java\jdk-17"; .\gradlew.bat assembleDebug --console=plain --max-workers=2 "-Pkotlin.compiler.execution.strategy=in-process"
  ```
  O artefato de debug usa a chave de desenvolvimento padrão do Android e não deve depender do material de assinatura de release.

- **Assinatura de Release:**
  `assembleRelease` não é critério de release até que a nova chave seja configurada fora do Git e o caminho de atualização das instalações existentes seja validado. O workflow de tags deve permanecer bloqueado até essa configuração.

- **Proibição de Alucinação:**
  - Nunca declare pronto algo que não foi executado de verdade no terminal.
  - Diferencie: código inspecionado, hipótese, teste executado e comportamento observado.

---

## Convenções de Commit e Governança

Siga o padrão Conventional Commits com mensagens claras, técnicas e em inglês ou português objetivo:

- `feat:` Nova funcionalidade no app ou motor de parsing.
- `fix:` Correção de bug ou falha de regra de negócio.
- `test:` Inclusão ou ajuste de testes unitários e de integração.
- `docs:` Atualização de documentação técnica ou manuais.
- `ci:` Alteração em pipelines de integração contínua (GitHub Actions).
- `chore:` Atualizações de dependências, wrappers ou configurações.
- `GOAL #XX:` Entregas abrangentes de engenharia com marcos executáveis.
