<h1 align="center">CardControl</h1>

<p align="center">
  Controle financeiro e rateio de faturas com captura passiva de notificações bancárias em tempo real.<br>
  Totalmente offline, com divisão exata de centavos e cofre criptografado no dispositivo.
</p>

<p align="center">
  <a href="dist/CardControl-v2.1.0-release.apk"><strong>Baixar APK de Release (v2.1.0) →</strong></a>
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.06.00-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Room SQLite" src="https://img.shields.io/badge/SQLite-Room%202.6.1-003B57?logo=sqlite&logoColor=white">
  <img alt="Arquitetura" src="https://img.shields.io/badge/Arquitetura-100%25%20Offline--First-070A12">
  <img alt="Licença MIT" src="https://img.shields.io/badge/licen%C3%A7a-MIT-green">
</p>

---

## Sumário

- [A proposta](#a-proposta)
- [Funcionalidades](#funcionalidades)
- [Arquitetura](#arquitetura)
- [Decisões de engenharia](#decisões-de-engenharia)
- [Como compilar e executar](#como-compilar-e-executar)
- [Testes e verificação](#testes-e-verificação)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Segurança e privacidade](#segurança-e-privacidade)
- [Distribuição e instalação](#distribuição-e-instalação)
- [Autor](#autor)
- [Licença](#licença)

---

## A proposta

Planilhas manuais e aplicativos conectados a agregadores costumam falhar onde mais deveriam ajudar: compras parceladas acumulam centavos perdidos por arredondamento impreciso, lançamentos manuais são esquecidos no dia a dia, e ferramentas conectadas por open banking exigem senhas bancárias e enviam extratos privados para servidores de terceiros.

O **CardControl** foi desenvolvido sobre uma premissa contrária: **a privacidade e a precisão contábil do usuário são absolutas**, e toda a arquitetura do aplicativo se subordina a isso.

Na prática, o sistema adota três compromissos fundamentais:

**1. Zero telemetria e custo zero de infraestrutura.**
Nenhum dado financeiro, fatura, cartão ou nome de titular sai do aparelho. Não existem bancos de dados em nuvem, analytics externos ou rastreadores. O aplicativo funciona plenamente sem internet e com R$ 0 de custo.

**2. Fechamento contábil e centavos exatos.**
Divisões de parcelas não sofrem com imprecisões de ponto flutuante. Toda fração monetária é operada em `BigDecimal` com truncamento na base e alocação do resto na primeira parcela, garantindo que a soma das parcelas seja rigorosamente igual ao valor cobrado pelo banco.

**3. Captura passiva sem intervenção manual e sem coleta de senhas.**
Em vez de exigir credenciais de open banking, o aplicativo escuta em segundo plano os eventos legítimos de push dos aplicativos bancários instalados no celular, higieniza os campos contra injeção e vincula a compra ao cartão e titular correspondentes com deduplicação criptográfica SHA-256.

---

## Funcionalidades

| Área | O que faz |
| --- | --- |
| **Faturas** | Consolidado mensal bruto, abatimentos acumulados e saldo líquido restante |
| **Titulares e Sublimites** | Controle de pessoas e dependentes com tetos de gastos e barras de progresso visual |
| **Cartões de Crédito** | Cadastro de cartões com dias de fechamento e vencimento, limite global e titular padrão |
| **Captura em Tempo Real** | Leitor passivo de notificações para Nubank, Inter, Itaú, C6, Santander, Bradesco, Caixa, PicPay e Mercado Pago |
| **Deduplicação** | Hash SHA-256 de pacote, valor, loja e final do cartão com janela de 2 minutos para evitar compras repetidas |
| **Rateio e WhatsApp** | Geração de faturas formatadas em PDF nativo e mensagem estruturada para envio direto no WhatsApp |
| **Abatimentos e Baixas** | Registro de pagamentos parciais ou Pix para restauração imediata do limite disponível |
| **Alertas por E-mail** | Envio opcional direto por SMTP TLS com saldo restante pós-compra do titular |
| **Interface Liquid Glass** | Design System em Jetpack Compose com tema Obsidian, ambient glow e teclados especializados |

### Captura em segundo plano

O serviço `BankNotificationListener` estende `NotificationListenerService` nativo do Android. Ele não executa loops de consulta periódica nem drena bateria: o sistema operacional notifica o serviço somente quando um dos pacotes bancários autorizados publica um alerta de compra. A partir daí, o texto é sanitizado, as parcelas e estabelecimentos são extraídos via Regex e o registro é inserido no banco de dados SQLite local.

---

## Arquitetura

```
Push Bancário (Android OS)
   │
   └── NotificationListenerService (BankNotificationListener)
                │
                ├── Sanitização de entrada (SecuritySanitizer)
                ├── Extração por Regex (BankNotificationParser)
                ├── Deduplicação SHA-256 (janela temporal de 120s)
                │
                └── CardRepository
                         │
                         ├── Cálculo de fechamento (closingDay)
                         ├── Divisão exata de parcelas (BigDecimal)
                         │
                         ├── Room Database ◄── SQLite privado (card_control_offline.db)
                         │        │
                         │        └── StateFlow ──► Interface (Jetpack Compose)
                         │
                         └── Alerta opcional ──► CredentialStore (Android Keystore AES256-GCM)
                                                       │
                                                       └── Envio direto SMTP TLS
```

| Camada | Escolha Técnica | Papel no Sistema |
| --- | --- | --- |
| Interface | Jetpack Compose + Material 3 | Telas responsivas, tema Liquid Glass e formulários inteligentes |
| Persistência | Room 2.6.1 (SQLite privado) | Armazenamento local estruturado com chaves estrangeiras e integridade referencial |
| Processamento | Coroutines + StateFlow | Fluxos reativos e assíncronos desacoplados da thread principal de UI |
| Automação | NotificationListenerService | Recepção de eventos de push bancário diretamente do sistema operacional |
| Segurança | Android Keystore + SecurityCrypto | Criptografia em hardware AES256-GCM para credenciais de e-mail opcionais |
| Higienização | SecuritySanitizer | Defesa contra CRLF injection em SMTP, Path Traversal em PDF e limites para ReDoS |

---

## Decisões de engenharia

**Persistência 100% offline em SQLite local em vez de backend em nuvem.**
Elimina qualquer risco de vazamento de faturas, custo de hospedagem ou dependência de conectividade. Os dados são privados da partição do aplicativo.

**Divisão com resto concentrado na primeira parcela.**
Dividir R$ 100,00 em 3 parcelas não pode gerar 3x de R$ 33,33 (faltando 1 centavo) nem 3x de R$ 33,34 (cobrando 2 centavos a mais). O algoritmo trunca em R$ 33,33 e soma a sobra de R$ 0,01 na primeira parcela (1ª de R$ 33,34 e demais de R$ 33,33).

**Captura por notificações do sistema em vez de Open Banking.**
APIs bancárias corporativas exigem certificados caros, homologações burocráticas ou cobrança por requisição. A leitura de notificações locais tem custo R$ 0, não solicita credenciais bancárias do usuário e funciona instantaneamente.

**Deduplicação temporal por hash SHA-256.**
Aplicativos bancários frequentemente reemitem a mesma notificação ao restabelecer sinal ou alternar redes. O hash combinando pacote, loja, valor, final do cartão e janela de 120 segundos descarta duplicidades sem bloquear compras legítimas consecutivas.

**Criptografia no hardware do aparelho (Android Keystore).**
Caso o usuário configure envio de e-mails para dependentes, a senha de aplicativo nunca é gravada em texto plano. É utilizada a cifra `AES256-GCM` amarrada ao hardware de segurança do dispositivo via `EncryptedSharedPreferences`.

---

## Como compilar e executar

Requer **JDK 17** e **Android SDK (API 34)** com `build-tools 34.0.0`.

```bash
# Compilar e executar a suíte de testes unitários
./gradlew testDebugUnitTest

# Compilar o APK de release otimizado e assinado
./gradlew assembleRelease
```

O arquivo compilado será gerado em: `app/build/outputs/apk/release/app-release.apk`.

| Comando | O que faz |
| --- | --- |
| `./gradlew testDebugUnitTest` | Executa todas as asserções de segurança e domínio |
| `./gradlew assembleDebug` | Compila o APK de desenvolvimento |
| `./gradlew assembleRelease` | Compila o binário de release otimizado e assinado |
| `apksigner verify --verbose <apk>` | Valida as assinaturas v1 e v2 do binário gerado |

---

## Testes e verificação

O repositório possui uma suíte determinística em `app/src/test/java/com/cardcontrol/app/SecurityAndDomainTestHarness.kt`.

| Teste | O que valida |
| --- | --- |
| **Path Traversal** | Garante que nomes de titulares maliciosos não escapem da pasta de cache de PDFs |
| **Injeção CRLF** | Valida que quebras de linha em nomes de estabelecimentos não injetem cabeçalhos SMTP |
| **Filtro de Notificações** | Comprova a rejeição de mensagens de estorno, compras negadas e pacotes não bancários |
| **Resiliência a ReDoS** | Verifica o truncamento de entradas excessivas antes da execução de Regex |
| **Precisão de Centavos** | Valida a conservação exata da soma em parcelamentos ímpares |
| **Virada de Ano** | Testa o avanço de competência da fatura quando compras ocorrem após o fechamento de dezembro |

---

## Estrutura do repositório

| Caminho | Responsabilidade |
| --- | --- |
| `app/src/main/java/.../data/` | Entidades Room, AppDao, AppDatabase e CardRepository |
| `app/src/main/java/.../service/` | BankNotificationListener e BankNotificationParser |
| `app/src/main/java/.../security/` | Sanitização de strings e validações de segurança |
| `app/src/main/java/.../ui/` | ViewModel, telas Compose e componentes Liquid Glass |
| `app/src/test/` | Suíte de testes unitários do harness de domínio e segurança |
| `dist/` | Distribuições oficiais de release (.apk assinado e checksums) |
| `docs/architecture/` | Documentação arquitetural e contratos técnicos |
| `docs/guides/` | Tutoriais de configuração de permissões no Android 13/14 |
| `docs/security/` | Modelo de ameaças e mapeamento de controles |
| `scripts/` | Utilitários de automação e empacotamento de código |
| `.agent/` | Estado de engenharia, tarefas e planos executáveis para agentes de IA |
| `.github/workflows/` | Pipelines de CI e publicação automatizada de releases |

---

## Segurança e privacidade

- Dados financeiros residem exclusivamente no SQLite privado do aplicativo.
- Zero telemetria, zero bibliotecas de terceiros de publicidade ou monitoramento.
- O Android Manifest desativa backups em nuvem não autorizados (`android:allowBackup="false"`).
- As permissões solicitadas são estritamente `INTERNET` (para envio opcional de SMTP TLS caso configurado) e `BIND_NOTIFICATION_LISTENER_SERVICE` (para recepção de pushes).
- Detalhes adicionais sobre reporte responsável e modelo de ameaças em [SECURITY.md](SECURITY.md) e [`docs/security/MODELO_DE_AMEACAS.md`](docs/security/MODELO_DE_AMEACAS.md).

---

## Distribuição e instalação

O APK assinado pronto para instalação direta encontra-se versionado na pasta [`dist/`](dist/):
- **Arquivo:** [`dist/CardControl-v2.1.0-release.apk`](dist/CardControl-v2.1.0-release.apk)
- **Checksums:** [`dist/SHA256SUMS.txt`](dist/SHA256SUMS.txt)
- **Instruções:** [`dist/README.md`](dist/README.md)

Para liberar a leitura de notificações em versões recentes do Android, consulte [`docs/guides/CONFIGURACAO_E_PERMISSOES.md`](docs/guides/CONFIGURACAO_E_PERMISSOES.md).

---

## Autor

**Kauan Kelvin** — desenvolvedor de software

[LinkedIn](https://www.linkedin.com/in/kauan-kelvin/) · [Portfólio](https://kauankelvindev.vercel.app) · [GitHub](https://github.com/kauankelvin7)

---

## Licença

MIT — ver [LICENSE](LICENSE).
