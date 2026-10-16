package com.cardcontrol.app

import com.cardcontrol.app.service.BankNotificationParser
import com.cardcontrol.app.service.ParsedBankExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressão do parser único de notificações bancárias.
 *
 * Os textos abaixo reproduzem a estrutura de notificações públicas desses bancos
 * (valor, estabelecimento, final do cartão, parcelas); não são capturas de dispositivos reais.
 */
class BankNotificationParserTest {

    private fun parseOk(pkg: String, title: String?, text: String?): ParsedBankExpense {
        val result = BankNotificationParser.parse(pkg, title, text)
        assertNotNull("Esperava compra reconhecida para: $title | $text", result)
        return result!!
    }

    // ---------- Formatos por banco suportado ----------

    @Test
    fun `nubank - compra a vista com final do cartao`() {
        val r = parseOk(
            "com.nu.production",
            "Compra aprovada",
            "Compra de R$ 45,90 APROVADA em PADARIA CENTRAL para o cartão com final 4321."
        )
        assertEquals("Nubank", r.bankName)
        assertEquals(4590L, r.amountCents)
        assertEquals("PADARIA CENTRAL", r.merchant)
        assertEquals(1, r.installments)
        assertEquals("4321", r.lastFourDigits)
    }

    @Test
    fun `itau - compra parcelada em milhar ignora valor da parcela`() {
        val r = parseOk(
            "com.itau",
            "Itaú",
            "Compra aprovada no valor de R$ 1.850,00 em AMAZON BR em 10x de R$ 185,00 cartão final 9988."
        )
        assertEquals("Itaú", r.bankName)
        assertEquals(185_000L, r.amountCents)
        assertEquals("AMAZON BR", r.merchant)
        assertEquals(10, r.installments)
        assertEquals("9988", r.lastFourDigits)
    }

    @Test
    fun `inter - compra no credito com valor antes do estabelecimento`() {
        val r = parseOk(
            "br.com.intermedium",
            "Compra aprovada",
            "Compra no crédito aprovada no cartão final 1234 no valor de R$ 50,00 em LOJAS RENNER."
        )
        assertEquals("Inter", r.bankName)
        assertEquals(5_000L, r.amountCents)
        assertEquals("LOJAS RENNER", r.merchant)
        assertEquals("1234", r.lastFourDigits)
    }

    @Test
    fun `c6 - estabelecimento delimitado por virgula`() {
        val r = parseOk(
            "com.c6bank.app",
            null,
            "Compra no crédito aprovada: R$ 89,90 em POSTO SHELL, cartão final 5678."
        )
        assertEquals("C6 Bank", r.bankName)
        assertEquals(8_990L, r.amountCents)
        assertEquals("POSTO SHELL", r.merchant)
        assertEquals("5678", r.lastFourDigits)
    }

    @Test
    fun `santander - data e hora nao viram parcelas`() {
        val r = parseOk(
            "com.santander.app",
            "Santander",
            "Compra aprovada no cartao final 1234 de R$ 120,00 em DROGASIL em 15/10 as 10:00"
        )
        assertEquals("Santander", r.bankName)
        assertEquals(12_000L, r.amountCents)
        assertEquals("DROGASIL", r.merchant)
        assertEquals(1, r.installments)
        assertEquals("1234", r.lastFourDigits)
    }

    @Test
    fun `bradesco cartoes - compra com data completa`() {
        val r = parseOk(
            "br.com.bradesco.cartoes",
            "Bradesco Cartões",
            "Compra aprovada no cartão final 4321 de R$ 35,50 em RESTAURANTE SABOR em 15/10/2026 às 12:30."
        )
        assertEquals("Bradesco Cartões", r.bankName)
        assertEquals(3_550L, r.amountCents)
        assertEquals("RESTAURANTE SABOR", r.merchant)
        assertEquals("4321", r.lastFourDigits)
    }

    @Test
    fun `bradesco - compra no debito`() {
        val r = parseOk(
            "com.bradesco",
            "Bradesco",
            "Compra no débito aprovada de R$ 22,00 em PADARIA PAO QUENTE, cartão final 0011."
        )
        assertEquals("Bradesco", r.bankName)
        assertEquals(2_200L, r.amountCents)
        assertEquals("PADARIA PAO QUENTE", r.merchant)
        assertEquals("0011", r.lastFourDigits)
    }

    @Test
    fun `caixa - estabelecimento antes do valor`() {
        val r = parseOk(
            "br.gov.caixa.cartoes",
            "CAIXA",
            "Compra aprovada em SUPERMERCADO BOM PRECO R$ 210,35 em 15/10 as 18:40, cartao final 1122."
        )
        assertEquals("Caixa", r.bankName)
        assertEquals(21_035L, r.amountCents)
        assertEquals("SUPERMERCADO BOM PRECO", r.merchant)
        assertEquals("1122", r.lastFourDigits)
    }

    @Test
    fun `picpay - final seguido de dois pontos`() {
        val r = parseOk(
            "com.picpay",
            "PicPay",
            "Compra aprovada no cartão PicPay final 3344: R$ 59,90 em IFOOD."
        )
        assertEquals("PicPay", r.bankName)
        assertEquals(5_990L, r.amountCents)
        assertEquals("IFOOD", r.merchant)
        assertEquals("3344", r.lastFourDigits)
    }

    @Test
    fun `mercado pago - estabelecimento delimitado por com seu cartao`() {
        val r = parseOk(
            "com.mercadopago.wallet",
            "Mercado Pago",
            "Compra aprovada de R$ 150,00 em MERCADOLIVRE com seu cartão final 7788."
        )
        assertEquals("Mercado Pago", r.bankName)
        assertEquals(15_000L, r.amountCents)
        assertEquals("MERCADOLIVRE", r.merchant)
        assertEquals("7788", r.lastFourDigits)
    }

    // ---------- Valores monetários ----------

    @Test
    fun `converte formatos monetarios sem perder centavos`() {
        assertEquals(185_000L, BankNotificationParser.parseAmountToCents("1.850,00"))
        assertEquals(185_000L, BankNotificationParser.parseAmountToCents("1850,00"))
        assertEquals(185_000L, BankNotificationParser.parseAmountToCents("1850.00"))
        assertEquals(185_000L, BankNotificationParser.parseAmountToCents("1,850.00"))
        assertEquals(10L, BankNotificationParser.parseAmountToCents("0,10"))
        assertEquals(123_457L, BankNotificationParser.parseAmountToCents("1.234,57"))
        assertEquals(99_999_999L, BankNotificationParser.parseAmountToCents("999.999,99"))
    }

    @Test
    fun `aceita BRL 1850 ponto 00 e BRL com separador de milhar`() {
        val dotDecimal = parseOk(
            "com.nu.production", "Compra aprovada",
            "Compra aprovada de BRL 1850.00 em APPLE.COM/BILL cartão final 4321."
        )
        assertEquals(185_000L, dotDecimal.amountCents)
        assertEquals("APPLE.COM/BILL", dotDecimal.merchant)

        val withThousands = parseOk(
            "com.nu.production", "Compra aprovada",
            "Compra aprovada de BRL 1,850.00 em STEAM GAMES cartão final 4321."
        )
        assertEquals(185_000L, withThousands.amountCents)
    }

    @Test
    fun `preserva centavos impares exatos`() {
        val r = parseOk(
            "com.nu.production", null,
            "Compra aprovada de R$ 1.234,57 em LOJA CENTRO cartão final 4321."
        )
        assertEquals(123_457L, r.amountCents)
    }

    @Test
    fun `rejeita valor sem centavos, zero ou acima do limite`() {
        assertNull(BankNotificationParser.parse("com.nu.production", null, "Compra aprovada de R$ 1.850 em LOJA X cartão final 4321."))
        assertNull(BankNotificationParser.parse("com.nu.production", null, "Compra aprovada de R$ 0,00 em LOJA X cartão final 4321."))
        assertNull(BankNotificationParser.parse("com.nu.production", null, "Compra aprovada de R$ 1.000.000,01 em LOJA X cartão final 4321."))
    }

    @Test
    fun `ignora limite disponivel ao extrair o valor da compra`() {
        val r = parseOk(
            "com.nu.production", "Compra aprovada",
            "Compra aprovada de R$ 80,00 em FARMACIA SAO JOAO, cartão final 4321. Limite disponível: R$ 1.920,00"
        )
        assertEquals(8_000L, r.amountCents)
        assertEquals("FARMACIA SAO JOAO", r.merchant)
    }

    @Test
    fun `valores distintos ambiguos nao geram lancamento`() {
        assertNull(
            BankNotificationParser.parse(
                "com.nu.production", "Compra aprovada",
                "Compra aprovada de R$ 100,00 em LOJA X cartão final 4321, ganhe R$ 10,00 de volta."
            )
        )
    }

    @Test
    fun `somente valor da parcela sem total nao gera lancamento`() {
        assertNull(
            BankNotificationParser.parse(
                "com.itau", null,
                "Compra aprovada em AMAZON BR em 10x de R$ 185,00 cartão final 9988."
            )
        )
    }

    // ---------- Título/texto nulos ou vazios ----------

    @Test
    fun `titulo e texto nulos ou vazios sao ignorados`() {
        assertNull(BankNotificationParser.parse("com.nu.production", null, null))
        assertNull(BankNotificationParser.parse("com.nu.production", "", ""))
        assertNull(BankNotificationParser.parse("com.nu.production", "   ", "\n\t"))
    }

    @Test
    fun `compra apenas no titulo com texto nulo e reconhecida`() {
        val r = parseOk("com.nu.production", "Compra aprovada de R$ 10,00 em LOJA ABC", null)
        assertEquals(1_000L, r.amountCents)
        assertEquals("LOJA ABC", r.merchant)
        assertNull(r.lastFourDigits)
    }

    // ---------- Mensagens que não são compra ----------

    @Test
    fun `mensagens sem compra sao ignoradas`() {
        val pkg = "com.nu.production"
        assertNull(BankNotificationParser.parse(pkg, "Pix recebido", "Você recebeu um Pix de R$ 50,00 de JOAO SILVA."))
        assertNull(BankNotificationParser.parse(pkg, "Transferência", "Transferência de R$ 100,00 realizada em CONTA DESTINO."))
        assertNull(BankNotificationParser.parse(pkg, "Seu cartão chegou", "Desbloqueie agora pelo app."))
        assertNull(BankNotificationParser.parse(pkg, "Pagamento", "Você pagou R$ 30,00 para MARIA SOUZA."))
        assertNull(BankNotificationParser.parse(pkg, "Código", "Seu código de verificação é 123456."))
    }

    @Test
    fun `fatura, cashback e ofertas sao ignorados mesmo com valor`() {
        val pkg = "com.nu.production"
        assertNull(BankNotificationParser.parse(pkg, "Sua fatura fechou", "Sua fatura fechou no valor de R$ 2.340,10 e vence dia 10."))
        assertNull(BankNotificationParser.parse("br.com.intermedium", "Oferta especial!", "Você ganhou aumento de limite de R$ 500,00 no seu cartão."))
        assertNull(BankNotificationParser.parse(pkg, "Cashback", "Compra aprovada em LOJA X rendeu cashback de R$ 5,00."))
        assertNull(BankNotificationParser.parse(pkg, "Promoção", "Compra aprovada com 10% de desconto em LOJA X: R$ 90,00."))
        assertNull(BankNotificationParser.parse(pkg, "Estorno", "Compra aprovada de R$ 90,00 em LOJA X foi estornada."))
        assertNull(BankNotificationParser.parse(pkg, "Compra negada", "Compra de R$ 90,00 em LOJA X negada no cartão final 4321."))
    }

    // ---------- Estabelecimento ----------

    @Test
    fun `estabelecimento ausente nao gera lancamento`() {
        assertNull(BankNotificationParser.parse("com.nu.production", "Compra aprovada", "Compra aprovada de R$ 30,00 no cartão final 4321."))
        assertNull(BankNotificationParser.parse("com.nu.production", "Compra aprovada", "Compra aprovada em cartão final 4321 de R$ 30,00."))
    }

    @Test
    fun `estabelecimento nao absorve palavras como cartao ou compra`() {
        val withCard = parseOk("com.nu.production", null, "Compra aprovada de R$ 45,90 em PADARIA CENTRAL cartão final 4321")
        assertEquals("PADARIA CENTRAL", withCard.merchant)

        val withPurchase = parseOk("com.nu.production", null, "Compra aprovada de R$ 45,90 em PADARIA CENTRAL compra no crédito final 4321")
        assertEquals("PADARIA CENTRAL", withPurchase.merchant)

        assertFalse(withCard.merchant.contains("cart", ignoreCase = true))
        assertFalse(withPurchase.merchant.contains("compra", ignoreCase = true))
    }

    // ---------- Parcelas ----------

    @Test
    fun `parcelas invalidas ou fora do limite nao geram lancamento`() {
        val pkg = "com.itau"
        assertNull(BankNotificationParser.parse(pkg, null, "Compra aprovada de R$ 100,00 em LOJA X em 0x cartão final 9988."))
        assertNull(BankNotificationParser.parse(pkg, null, "Compra aprovada de R$ 100,00 em LOJA X em 99x cartão final 9988."))
        assertNull(BankNotificationParser.parse(pkg, null, "Compra aprovada de R$ 100,00 em LOJA X em 150x cartão final 9988."))
        assertNull(BankNotificationParser.parse(pkg, null, "Compra aprovada de R$ 100,00 em LOJA X em 3x, cartão final 9988, parcelado em 4x."))
    }

    @Test
    fun `parcelas dentro do limite sao aceitas`() {
        val twelve = parseOk("com.itau", null, "Compra aprovada de R$ 1.200,00 em LOJA X em 12 vezes, cartão final 9988.")
        assertEquals(12, twelve.installments)
        val max = parseOk("com.itau", null, "Compra aprovada de R$ 7.200,00 em LOJA X em ${BankNotificationParser.MAX_INSTALLMENTS}x cartão final 9988.")
        assertEquals(BankNotificationParser.MAX_INSTALLMENTS, max.installments)
    }

    // ---------- Pacotes ----------

    @Test
    fun `pacotes nao suportados ou nao confirmados sao ignorados`() {
        val text = "Compra aprovada de R$ 500,00 em MERCADO LIVRE no cartao final 4321"
        listOf(
            "com.whatsapp",
            "br.com.xp.cartoes",
            "com.itaucard.activity",
            "br.com.santander.way",
            "com.google.android.apps.walletnfcrel",
            "com.samsung.android.spay",
            ""
        ).forEach { pkg ->
            assertFalse(pkg, BankNotificationParser.isSupportedPackage(pkg))
            assertNull(pkg, BankNotificationParser.parse(pkg, "Compra aprovada", text))
        }
        assertFalse(BankNotificationParser.isSupportedPackage(null))
        assertNull(BankNotificationParser.parse(null, "Compra aprovada", text))
    }

    @Test
    fun `lista de pacotes suportados e exatamente a confirmada`() {
        assertEquals(
            setOf(
                "com.nu.production", "br.com.intermedium", "com.itau", "com.c6bank.app",
                "com.santander.app", "com.bradesco", "br.com.bradesco.cartoes",
                "br.gov.caixa.cartoes", "com.picpay", "com.mercadopago.wallet"
            ),
            BankNotificationParser.SUPPORTED_PACKAGES
        )
    }

    // ---------- Final do cartão ----------

    @Test
    fun `cartao sem final e reconhecido com lastFour nulo`() {
        val r = parseOk("com.nu.production", "Compra aprovada", "Compra aprovada de R$ 25,00 em CAFE DO PONTO.")
        assertNull(r.lastFourDigits)
        assertEquals("CAFE DO PONTO", r.merchant)
    }

    @Test
    fun `cartao mascarado com asteriscos e reconhecido`() {
        val r = parseOk("com.nu.production", null, "Compra aprovada de R$ 25,00 em CAFE DO PONTO no cartão ****6543.")
        assertEquals("6543", r.lastFourDigits)
    }

    @Test
    fun `finais de cartao conflitantes nao geram lancamento`() {
        assertNull(
            BankNotificationParser.parse(
                "com.nu.production", null,
                "Compra aprovada de R$ 25,00 em CAFE DO PONTO cartão final 1111, adicional final 2222."
            )
        )
    }

    // ---------- Notificações repetidas ----------

    @Test
    fun `notificacoes repetidas geram o mesmo hash de deduplicacao`() {
        val text = "Compra de R$ 45,90 APROVADA em PADARIA CENTRAL para o cartão com final 4321."
        val first = parseOk("com.nu.production", "Compra aprovada", text)
        val second = parseOk("com.nu.production", "Compra aprovada", text)
        assertEquals(first.dedupHash, second.dedupHash)
        assertEquals(24, first.dedupHash.length)

        val otherAmount = parseOk("com.nu.production", "Compra aprovada", text.replace("45,90", "45,91"))
        assertNotEquals(first.dedupHash, otherAmount.dedupHash)
        val otherBank = parseOk("com.itau", "Compra aprovada", text)
        assertNotEquals(first.dedupHash, otherBank.dedupHash)
    }

    @Test
    fun `entrada gigante e limitada sem quebrar o parser`() {
        val noisy = "Compra aprovada de R$ 10,00 em LOJA ABC. " + "A".repeat(50_000)
        val r = BankNotificationParser.parse("com.nu.production", null, noisy)
        assertTrue(r == null || r.amountCents == 1_000L)
    }
}