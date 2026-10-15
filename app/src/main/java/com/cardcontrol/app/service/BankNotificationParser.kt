package com.cardcontrol.app.service

import com.cardcontrol.app.security.SecuritySanitizer
import java.security.MessageDigest

data class ParsedBankExpense(
    val amount: Double,
    val merchant: String,
    val lastFourDigits: String?,
    val installments: Int,
    val dedupHash: String
)

object BankNotificationParser {
    val SUPPORTED_PACKAGES = setOf(
        "com.nu.production",
        "br.com.intermedium",
        "com.itau",
        "com.itaucard.activity",
        "com.c6bank.app",
        "com.santander.app",
        "br.com.santander.way",
        "com.bradesco",
        "br.com.bradesco.cartoes",
        "br.gov.caixa.cartoes",
        "com.picpay",
        "com.mercadopago.wallet"
    )

    private val APPROVAL_KEYWORDS = listOf(
        "compra aprovada", "compra no credito", "compra no crédito",
        "compra no cartao", "compra no cartão", "transacao aprovada",
        "transação aprovada", "você comprou", "voce comprou", "compra realizada"
    )

    private val REJECTION_OR_REFUND_KEYWORDS = listOf(
        "negada", "recusada", "estorno", "estornada", "cancelada",
        "reembolso", "fatura fechada", "pagamento recebido", "pix enviado"
    )

    private val AMOUNT_REGEX = Regex("""R\$\s*([0-9]{1,3}(?:\.[0-9]{3})*,[0-9]{2}|[0-9]+,[0-9]{2})""", RegexOption.IGNORE_CASE)
    private val LAST_FOUR_REGEX = Regex("""(?:final|cart[aã]o|\*{2,4})\s*([0-9]{4})""", RegexOption.IGNORE_CASE)
    private val INSTALLMENTS_REGEX = Regex("""(?:em\s+)?([0-9]{1,2})\s*x\b""", RegexOption.IGNORE_CASE)
    private val MERCHANT_REGEX = Regex(
        """(?:em|no|na|estabelecimento:?)\s+([A-Za-z0-9À-ÿ\s\*.\-_&]{2,35}?)(?:\s+em\s+\d|\s+no\s+valor|\s+com\s+cart|\s+no\s+cart|\s+aprovad|\.|,|$)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(packageName: String, title: String, text: String, timestamp: Long): ParsedBankExpense? {
        if (packageName !in SUPPORTED_PACKAGES) return null

        val safeTitle = SecuritySanitizer.boundNotificationInput(title)
        val safeText = SecuritySanitizer.boundNotificationInput(text)
        val fullContent = "$safeTitle $safeText".trim()
        val normalized = fullContent.lowercase()

        if (REJECTION_OR_REFUND_KEYWORDS.any { normalized.contains(it) }) return null
        if (APPROVAL_KEYWORDS.none { normalized.contains(it) }) return null

        val amountMatch = AMOUNT_REGEX.find(fullContent)?.groupValues?.get(1) ?: return null
        val cleanAmount = amountMatch.replace(".", "").replace(",", ".").toDoubleOrNull() ?: return null
        if (cleanAmount <= 0.0 || cleanAmount > 1_000_000.0) return null

        val lastFour = LAST_FOUR_REGEX.find(fullContent)?.groupValues?.get(1)
        val rawInstallments = INSTALLMENTS_REGEX.find(fullContent)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val installments = SecuritySanitizer.validateInstallments(rawInstallments)

        val rawMerchant = MERCHANT_REGEX.find(fullContent)?.groupValues?.get(1)?.trim()
        val fallbackTitle = safeTitle.takeIf { it.isNotBlank() && !it.lowercase().contains("compra") }
        val merchant = SecuritySanitizer.sanitizeHeaderField(
            rawMerchant?.takeIf { it.isNotBlank() } ?: fallbackTitle ?: "Compra Automatica",
            maxLength = 40
        )

        val timeBucket = timestamp / 120_000L
        val hashInput = "$packageName|${lastFour ?: "0000"}|$cleanAmount|${merchant.lowercase()}|$timeBucket"

        return ParsedBankExpense(
            amount = cleanAmount,
            merchant = merchant,
            lastFourDigits = lastFour,
            installments = installments,
            dedupHash = sha256(hashInput)
        )
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }
}
