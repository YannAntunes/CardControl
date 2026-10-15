package com.cardcontrol.app

import com.cardcontrol.app.service.BankNotificationMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankNotificationParserTest {

    @Test
    fun `deve identificar compra a vista no Nubank corretamente`() {
        val result = BankNotificationMatcher.parse(
            packageName = "com.nu.production",
            title = "Compra no crédito aprovada",
            text = "Compra aprovada de R$ 45,90 em PADARIA CENTRAL cartão final 4321."
        )

        assertNotNull(result)
        assertEquals("Nubank", result?.bankName)
        assertEquals(45.90, result?.amount ?: 0.0, 0.001)
        assertEquals("PADARIA CENTRAL", result?.merchant)
        assertEquals(1, result?.installments)
        assertEquals("4321", result?.lastFourDigits)
    }

    @Test
    fun `deve identificar compra parcelada no Itau com valor na casa de milhar`() {
        val result = BankNotificationMatcher.parse(
            packageName = "com.itau",
            title = "Itaú Unibanco",
            text = "Compra aprovada no valor de R$ 1.850,00 em AMAZON BR em 10x cartão final 9988."
        )

        assertNotNull(result)
        assertEquals("Itaú", result?.bankName)
        assertEquals(1850.00, result?.amount ?: 0.0, 0.001)
        assertEquals("AMAZON BR", result?.merchant)
        assertEquals(10, result?.installments)
        assertEquals("9988", result?.lastFourDigits)
    }

    @Test
    fun `deve ignorar falsos positivos como fechamento de fatura ou promocoes`() {
        val invoiceNotification = BankNotificationMatcher.parse(
            packageName = "com.nu.production",
            title = "Sua fatura fechou",
            text = "Sua fatura fechou no valor de R$ 2.340,10 e vence dia 10."
        )

        val promoNotification = BankNotificationMatcher.parse(
            packageName = "br.com.intermedium",
            title = "Oferta especial!",
            text = "Você ganhou aumento de limite de R$ 500,00 no seu cartão."
        )

        assertNull(invoiceNotification)
        assertNull(promoNotification)
    }

    @Test
    fun `deve validar pacotes de bancos suportados`() {
        assertTrue(BankNotificationMatcher.isSupportedBankPackage("com.nu.production"))
        assertTrue(BankNotificationMatcher.isSupportedBankPackage("br.com.xp.cartoes"))
        assertTrue(BankNotificationMatcher.isSupportedBankPackage("com.mercadopago.wallet"))
    }
}