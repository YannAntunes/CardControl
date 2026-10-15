# Plano de Execução do CardControl (ExecPlan)

## 1. Objetivo

Fornecer um gerenciador de cartões de crédito e rateio de faturas com captura 100% offline em tempo real, sem dependência de serviços externos ou custos de nuvem, respeitando os limites dos sublimites individuais de cada titular.

---

## 2. Contexto Atual e Restrições Técnicas

- **Linguagem / SDK:** Kotlin 1.9.24, Android SDK API 34 (Android 14), Java 17.
- **Ambiente de Build:** Gradle 8.7 Wrapper (`./gradlew.bat`), KSP para compilação de anotações do Room.
- **Hardware do Dispositivo:** Testado e aprovado em dispositivo físico com Android 14.
- **Restrição de Conexão:** Nenhuma chamada para APIs externas de telemetria ou armazenamento.

---

## 3. Não Objetivos

- Não conectar serviços de terceiros de open banking que exijam senhas ou credenciais bancárias.
- Não enviar cópias de faturas para servidores remotos de analytics.
- Não utilizar bibliotecas pesadas de terceiros que aumentem desnecessariamente o tamanho do APK.

---

## 4. Contratos e Invariantes

- **Invariante Monetária:** O valor total da transação deve ser exatamente igual à soma de suas parcelas (`splitInstallmentAmounts`), com o resto de arredondamento concentrado na primeira parcela.
- **Invariante de Fechamento:** Compras efetuadas no dia de fechamento ou após devem ser alocadas para o mês subsequente.
- **Invariante de Segurança:** Todos os textos extraídos de notificações ou entradas de usuário passam por `SecuritySanitizer` antes de persistência ou exibição.

---

## 5. Marcos de Entrega

- [x] **M1 - Fundação e Banco Room:** Entidades `HolderEntity`, `CreditCardEntity`, `TransactionEntity`, `InstallmentEntity`, `PaymentEntity`.
- [x] **M2 - Motor de Notificações e TestHarness:** Extração e deduplicação SHA-256 de 9 bancos com testes automatizados.
- [x] **M3 - Interface Liquid Glass e Formulários:** Compose moderno com dark theme, adaptive icons e teclados dedicados.
- [x] **M4 - Chave de Release e Automação:** Assinatura v2, liberação do serviço no Android e documentação de agentes.
- [ ] **M5 - Conciliação Automática de Pix:** Leitura de Pix recebido para baixa de faturas.
