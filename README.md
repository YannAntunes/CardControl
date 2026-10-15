# CardControl 💳✨

[![Android CI](https://github.com/kauankelvin7/CardControl/actions/workflows/ci.yml/badge.svg)](https://github.com/kauankelvin7/CardControl/actions)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Architecture](https://img.shields.io/badge/Architecture-100%25%20Offline--First-070A12.svg?logo=sqlite&logoColor=white)](https://github.com/kauankelvin7/CardControl)
[![Design](https://img.shields.io/badge/Theme-Liquid%20Glass-38BDF8.svg)](https://github.com/kauankelvin7/CardControl)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> Aplicativo Android nativo para controle financeiro, gestão de cartões de crédito e rateio de faturas com **captura automática de notificações bancárias em tempo real**, operando de forma **100% offline-first**.

---

## 🌟 Principais Recursos

- **Leitura Automática por Push:** Detecta compras aprovadas em tempo real nos principais bancos do Brasil (Nubank, Inter, Itaú, C6, Santander, Bradesco, Caixa, PicPay e Mercado Pago).
- **Rateio e Sublimites:** Permite cadastrar múltiplos titulares/dependentes com tetos de gastos personalizados (sublimites) e barras de progresso dinâmicas.
- **Divisão com Precisão de Centavos:** Algoritmo financeiro baseado em `BigDecimal` que aloca a sobra de centavos na primeira parcela, garantindo fechamento contábil exato de qualquer parcelamento.
- **Regra de Fechamento Inteligente:** Aloca automaticamente a primeira parcela para o mês correto de acordo com a data de corte do cartão (`closingDay`), inclusive na virada de ano.
- **Relatórios Instantâneos:** Geração de faturas em PDF formatado e texto estruturado com envio em 1 toque para o **WhatsApp**.
- **Segurança e Criptografia:** Sem servidores remotos. Todas as credenciais de e-mail opcionais para alertas instantâneos são cifradas com `AES256-GCM` no **Android Keystore**.
- **Interface Liquid Glass:** Design fluido com tema dark *Obsidian*, ambient glow, formulários inteligentes com teclados numéricos e botões de ação rápida.

---

## 🛠️ Arquitetura e Tecnologias

- **Linguagem:** Kotlin 1.9.24
- **Interface:** Jetpack Compose (BOM 2024.06.00) com Material 3
- **Banco de Dados:** Room Database 2.6.1 (SQLite local nativo) com KSP
- **Serviço em Segundo Plano:** Android `NotificationListenerService`
- **Criptografia:** `androidx.security:security-crypto:1.1.0-alpha06`
- **Ambiente de Build:** Gradle 8.7 Wrapper (JDK 17)

---

## 🚀 Como Compilar e Testar

### Pré-requisitos
- JDK 17 configurado em `JAVA_HOME`.
- Android SDK com plataformas da API 34 instaladas.

### 1. Executar a Suíte de Testes Unitários
```bash
./gradlew testDebugUnitTest
```

### 2. Gerar o APK de Release Assinado
```bash
./gradlew assembleRelease
```
O binário será gerado em: `app/build/outputs/apk/release/app-release.apk`.

### 3. Verificar Assinatura do APK
```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

---

## 📋 Governança e Regras de Engenharia

O projeto adota diretrizes estritas de desenvolvimento e automação para agentes e mantenedores humanos:
- Consulte [AGENTS.md](AGENTS.md) para ordem de precedência, regras de arquitetura e validações obrigatórias.
- Consulte [.agent/CARDCONTROL_STATE.md](.agent/CARDCONTROL_STATE.md) para o estado atualizado do repositório e tarefas em andamento.
- Consulte [CONTRIBUTING.md](CONTRIBUTING.md) para regras de commits e fluxo de trabalho.
- Consulte [SECURITY.md](SECURITY.md) para detalhes do modelo de segurança offline e política de reporte.

---

## 📄 Licença

Distribuído sob licença MIT. Veja `LICENSE` para mais informações.
