# Brazilian Bank Push Notification Examples

Reference samples of real push notifications emitted by supported banks in Brazil:

### 1. Nubank (`com.nu.production`)
- **Single installment:**
  - Title: `Compra aprovada`
  - Text: `Você comprou R$ 89,90 em RESTAURANTE SABOR no cartão com final 1234.`
- **Installment purchase:**
  - Title: `Compra aprovada`
  - Text: `Você comprou R$ 1.200,00 em MAGAZINE LUIZA em 10x no cartão com final 1234.`
- **Estorno (rejection):**
  - Title: `Estorno de compra`
  - Text: `O estorno de R$ 89,90 em RESTAURANTE SABOR foi processado.`

### 2. Banco Inter (`br.com.intermedium`)
- **Single:**
  - Title: `Compra no Cartão Inter`
  - Text: `Transação aprovada de R$ 45,50 em FARMACIA PAGUE MENOS no cartão final 5678.`
- **Installments:**
  - Title: `Compra no Crédito`
  - Text: `Compra aprovada de R$ 600,00 em 3x no estabelecimento APPLE STORE.`

### 3. Itaú / Itaucard (`com.itau`, `com.itaucard.activity`)
- **Single:**
  - Title: `Itaucard: Compra Aprovada`
  - Text: `Compra aprovada de R$ 123,45 no cartao final 9012 em POSTO SHELL.`

### 4. C6 Bank (`com.c6bank.app`)
- **Single:**
  - Title: `Compra no cartão aprovada`
  - Text: `Você comprou R$ 78,00 em UBER no cartão final 3456.`

### 5. Santander / Santander Way (`com.santander.app`, `br.com.santander.way`)
- **Single:**
  - Title: `Santander Way: Compra Aprovada`
  - Text: `Compra aprovada no cartao final 7890 no valor de R$ 250,00 em PÃO DE AÇÚCAR.`

### 6. Mercado Pago (`com.mercadopago.wallet`)
- **Single:**
  - Title: `Pagamento aprovado`
  - Text: `Compra aprovada de R$ 34,90 no cartão Mercado Pago em MERCADO EXTRA.`

### 7. Pix Recebido (Proposta TASK-008)
- **Nubank:**
  - Title: `Transferência recebida`
  - Text: `Você recebeu uma transferência Pix de R$ 150,00 de JOAO DA SILVA.`
- **Inter:**
  - Title: `Pix Recebido`
  - Text: `Você recebeu um Pix de R$ 200,00 de MARIA SOUZA.`
