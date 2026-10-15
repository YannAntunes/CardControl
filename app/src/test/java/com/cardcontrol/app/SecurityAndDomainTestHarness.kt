package com.cardcontrol.app

import com.cardcontrol.app.data.repository.CardRepository
import com.cardcontrol.app.security.SecuritySanitizer
import com.cardcontrol.app.service.BankNotificationParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SecurityAndDomainTestHarness {

    @Test
    fun `harness deve bloquear tentativa de Path Traversal no nome do titular`() {
        val maliciousName = "../../etc/passwd\u0000; rm -rf"
        val sanitized = SecuritySanitizer.sanitizeFileNameSegment(maliciousName)
        assertFalse(sanitized.contains(".."))
        assertFalse(sanitized.contains("/"))
        assertEquals("etcpasswd_rm_-rf", sanitized)
    }

    @Test
    fun `harness deve neutralizar injecao de cabecalho SMTP CRLF`() {
        val maliciousMerchant = "LOJA X\r\nBcc: atacante@dominio.com\r\nSubject: Hacked"
        val sanitized = SecuritySanitizer.sanitizeHeaderField(maliciousMerchant)
        assertFalse(sanitized.contains("\r"))
        assertFalse(sanitized.contains("\n"))
        assertTrue(sanitized.startsWith("LOJA X Bcc:"))
    }

    @Test
    fun `harness deve ignorar notificacoes de compra negada ou estorno`() {
        val denied = BankNotificationParser.parse(
            packageName = "com.nu.production",
            title = "Compra negada",
            text = "Compra aprovada anteriormente foi estornada no valor de R$ 350,00 no cartao final 4321"
        )
        assertNull(denied)
    }

    @Test
    fun `harness deve rejeitar pacotes nao bancarios mesmo com texto identico`() {
        val spoofed = BankNotificationParser.parse(
            packageName = "com.whatsapp",
            title = "Compra aprovada",
            text = "Compra aprovada de R$ 500,00 em MERCADO LIVRE no cartao final 4321"
        )
        assertNull(spoofed)
    }

    @Test
    fun `harness deve truncar payload gigante evitando ReDoS`() {
        val giantNoise = "A".repeat(50_000)
        val bounded = SecuritySanitizer.boundNotificationInput(giantNoise)
        assertEquals(1000, bounded.length)
    }

    @Test
    fun `harness deve manter soma exata de centavos em parcelamentos impares`() {
        val parcels = CardRepository.splitInstallmentAmounts(1000.00, 7)
        assertEquals(7, parcels.size)
        assertEquals(142.90, parcels.first(), 0.001)
        assertEquals(142.85, parcels.last(), 0.001)
        assertEquals(1000.00, parcels.sum(), 0.0001)
    }

    @Test
    fun `harness deve avancar fatura na virada de ano quando compra ocorre apos fechamento de dezembro`() {
        val decPurchaseAfterClosing = LocalDate.of(2026, 12, 15)
        val firstParcelDate = CardRepository.calculateFirstInstallmentMonth(decPurchaseAfterClosing, closingDay = 10)
        assertEquals(2027, firstParcelDate.year)
        assertEquals(1, firstParcelDate.monthValue)
    }
}
