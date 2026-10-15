# CardControl — Estado Atual do Repositório

Última atualização: 15/10/2026
Branch de desenvolvimento da correção: `fix/repository-integrity`

## Ponto Exato de Retomada

A versão **v2.1.0 (versionCode 3)** foi compilada e instalada anteriormente por sideload, conforme os registros históricos abaixo. Depois dessa entrega, foi identificado que o keystore de release e as senhas de assinatura foram versionados no repositório público. Esse material deve ser tratado como comprometido.

A correção de segurança remove o keystore da árvore atual e o uso das senhas no Gradle. Builds `debug` usam a assinatura de desenvolvimento padrão; a assinatura de release permanece desativada. Não publicar uma nova versão até gerar uma nova chave, definir como as instalações existentes serão atualizadas e validar a nova assinatura. A exclusão no commit atual não remove cópias presentes no histórico Git, forks, clones ou caches.

A permissão de leitor de notificações (`NotificationListenerService`) foi autorizada anteriormente no aparelho do usuário; esse fato não equivale a uma nova validação do build atual.

---

## Inventário de Módulos e Componentes

| Módulo / Arquivo | Responsabilidade | Status de Validação |
| :--- | :--- | :--- |
| `BankNotificationParser.kt` | Extração de valor, loja, cartão e parcelas. Deduplicação SHA-256 de 120s. | Validar contra os testes atuais antes de declarar cobertura completa |
| `BankNotificationListener.kt` | Serviço em segundo plano com permissão de escuta de notificações. Dispara lançamentos e e-mails. | Permissão validada anteriormente no Android; integração atual deve ser retestada ao alterar o parser |
| `SecuritySanitizer.kt` | Defesa contra CRLF injection em SMTP, Path Traversal em relatórios PDF e controle de ReDoS. | Cobertura registrada em testes anteriores; não revalidada por esta correção de assinatura |
| `CardRepository.kt` | Regra de negócio financeira, cálculo de corte de fatura (`closingDay`) e divisão de centavos `BigDecimal`. | Cobertura registrada em testes anteriores; não revalidada por esta correção de assinatura |
| `AppDatabase.kt` / `AppDao.kt` | Banco de dados Room SQLite com persistência 100% offline local (`card_control_offline.db`). | Migrations e KSP validados anteriormente; preservar compatibilidade nas atualizações de dependências |
| `CredentialStore.kt` | Cofre de credenciais SMTP criptografado com `EncryptedSharedPreferences` via Android Keystore. | Integrado anteriormente; não confundir com o keystore de assinatura do APK |
| `LiquidGlass.kt` | Design system com tema dark *Obsidian*, ambient glow ciano/índigo, barras de progresso e pill selectors. | Renderização Compose validada anteriormente |
| `MainScreen.kt` | Interface de usuário (Faturas, Cartões, Automação), com diálogos estilizados e teclados especializados. | Compilado e testado em tela real anteriormente |
| `SecurityAndDomainTestHarness.kt` | Suíte de testes unitários de domínio, parcelamento, sanitização e datas. | Último resultado registrado anteriormente: 7/7; executar o CI atual antes de repetir essa afirmação |

---

## Evidências dos Gates de Engenharia

- **Histórico de build:** os registros anteriores indicam `BUILD SUCCESSFUL` para `testDebugUnitTest assembleRelease`, com o keystore então versionado. Esse resultado é histórico e não comprova que um novo release possa ser assinado com segurança.
- **Estado da assinatura:** o release build está intencionalmente sem configuração de assinatura enquanto a rotação não for concluída. O workflow de tags deve permanecer bloqueado nesse período.
- **Próxima validação:** conferir os resultados de Unit tests, Android Lint e CodeQL no PR que remove o material de assinatura. Não tratar checks `skipped` como aprovados.
- **Governança pendente:** habilitar proteção de `main`, ativar o Dependency Graph e confirmar que Dependency Review/Submission executam sem serem ignorados.

---

## Próximos Objetivos Disponíveis no Grafo de Tarefas

1. Implementar parser automático de notificações de **Pix recebido** para abatimento de faturas de titulares cadastrados.
2. Adicionar filtros por categoria ou estabelecimento na listagem de compras do mês.
3. Exportação de backup completo do banco de dados SQLite para arquivo JSON ou CSV local.
4. Concluir a rotação da chave de assinatura e validar a estratégia de atualização de instalações existentes.
5. Ativar proteção de branch e verificações de dependências no GitHub.
