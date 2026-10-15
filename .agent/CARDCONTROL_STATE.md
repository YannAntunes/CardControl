# CardControl — Estado Atual do Repositório

Última atualização: 15/10/2026
Branch atual: `main`

## Ponto Exato de Retomada

O projeto encontra-se na versão **v2.1.0 (versionCode 3)** totalmente funcional e testado em ambiente de execução real.
O binário compilado e assinado com certificado dedicado (`release.keystore`, RSA 2048-bit) foi instalado e aprovado no aparelho do usuário via depuração local.
A permissão de leitor de notificações (`NotificationListenerService`) foi autorizada com sucesso nas configurações restritas do Android.

---

## Inventário de Módulos e Componentes

| Módulo / Arquivo | Responsabilidade | Status de Validação |
| :--- | :--- | :--- |
| `BankNotificationParser.kt` | Extração de valor, loja, cartão e parcelas de 9 apps bancários. Deduplicação SHA-256 de 120s. | Testado (TestHarness) |
| `BankNotificationListener.kt` | Serviço em segundo plano com permissão de escuta de notificações. Dispara lançamentos e e-mails. | Autorizado e ativo no Android |
| `SecuritySanitizer.kt` | Defesa contra CRLF injection em SMTP, Path Traversal em relatórios PDF e controle de ReDoS. | 100% de cobertura nos testes |
| `CardRepository.kt` | Regra de negócio financeira, cálculo de corte de fatura (`closingDay`) e divisão de centavos `BigDecimal`. | 100% de cobertura nos testes |
| `AppDatabase.kt` / `AppDao.kt` | Banco de dados Room SQLite com persistência 100% offline local (`card_control_offline.db`). | Validado com migrations e KSP |
| `CredentialStore.kt` | Cofre de credenciais SMTP criptografado com `EncryptedSharedPreferences` via Android Keystore. | Integrado com Android Keystore |
| `LiquidGlass.kt` | Design system com tema dark *Obsidian*, ambient glow ciano/índigo, barras de progresso e pill selectors. | Renderização Compose validada |
| `MainScreen.kt` | Interface de usuário (Faturas, Cartões, Automação), com diálogos estilizados e teclados especializados. | Compilado e testado em tela real |
| `SecurityAndDomainTestHarness.kt` | Suíte de testes unitários com 7 verificações de domínio, parcelamento, sanitização e datas. | 7/7 PASS |

---

## Evidências dos Gates de Engenharia

- **Gradle Build & Unit Tests:**
  - Comando: `.\gradlew.bat testDebugUnitTest assembleRelease`
  - Status: `BUILD SUCCESSFUL` (73 actionable tasks, 0 falhas, 0 erros).
  - Testes: 7/7 aprovados em `SecurityAndDomainTestHarness`.
- **Assinatura do Pacote:**
  - `apksigner.bat verify --verbose CardControl.apk`: `Verified using v2 scheme: true`, Signer: `CN=CardControl, OU=Security, O=CardControl Offline, L=Sao Paulo, ST=SP, C=BR`.
  - Tamanho final do APK otimizado: **11.3 MB**.

---

## Próximos Objetivos Disponíveis no Grafo de Tarefas

1. Implementar parser automático de notificações de **Pix recebido** para abatimento de faturas de titulares cadastrados.
2. Adicionar filtros por categoria ou estabelecimento na listagem de compras do mês.
3. Exportação de backup completo do banco de dados SQLite para arquivo JSON ou CSV local.
