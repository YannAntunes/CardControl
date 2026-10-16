package com.cardcontrol.app.service

import com.cardcontrol.app.security.SecuritySanitizer
import com.cardcontrol.app.util.MoneyUtils
import java.security.MessageDigest

data class ParsedBankExpense(
    val amount: Long,
    val merchant: String,
    val lastFourDigits: String?,
    val installments: Int,
    val dedupHash: String
)

object BankNotificationParser {
    // Candidatos futuros para homologacao com dispositivo fisico:
    // "br.com.xp.cartoes", "com.google.android.apps.walletnfcrel", "com.samsung.android.spay"
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
        "transação aprovada", "você comprou", "voce comprou", "compra realizada",
        "pagamento aprovado", "você pagou", "voce pagou"
    )

    private val REJECTION_OR_REFUND_KEYWORDS = listOf(
        "negada", "recusada", "estorno", "estornada", "cancelada",
        "reembolso", "fatura fechada", "fatura fechou", "fatura vence",
        "fatura está disponível", "fatura esta disponivel", "pagamento recebido", "pix enviado",
        "aumento de limite", "limite liberado", "oferta de limite",
        "empréstimo pré-aprovado", "emprestimo pre-aprovado", "simulação de empréstimo", "simulacao de emprestimo",
        "oferta de empréstimo", "oferta de emprestimo", "código de verificação", "codigo de verificacao",
        "código de segurança", "codigo de seguranca", "token de segurança", "token de seguranca",
        "seu token é", "seu token:"
    )

    private val AMOUNT_REGEX = Regex("""R\$\s*([0-9]{1,3}(?:\.[0-9]{3})*,[0-9]{2}|[0-9]+,[0-9]{2})""", RegexOption.IGNORE_CASE)
    private val LAST_FOUR_REGEX = Regex("""(?:final|cart[aã]o(?:\s+final)?|terminado\s+em|\*{2,4})\s*([0-9]{4})""", RegexOption.IGNORE_CASE)
    private val INSTALLMENTS_REGEX = Regex("""(?:em\s+)?([0-9]{1,2})\s*x\b""", RegexOption.IGNORE_CASE)
    private val MERCHANT_REGEX = Regex(
        """(?:em|no|na|para|estabelecimento:?)\s+([A-Za-z0-9À-ÿ\s\*.\-_&]{2,35}?)(?:\s+em\s+\d|\s+no\s+valor|\s+com\s+cart|\s+no\s+cart|\s+cart[aã]o|\s+final|\s+aprovad|\.|,|$)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(packageName: String, title: String, text: String, @Suppress("UNUSED_PARAMETER") timestamp: Long = 0L): ParsedBankExpense? {
        if (packageName !in SUPPORTED_PACKAGES) return null

        val safeTitle = SecuritySanitizer.boundNotificationInput(title)
        val safeText = SecuritySanitizer.boundNotificationInput(text)
        val fullContent = "$safeTitle $safeText".trim()
        val normalized = fullContent.lowercase()

        if (REJECTION_OR_REFUND_KEYWORDS.any { normalized.contains(it) }) return null
        if (APPROVAL_KEYWORDS.none { normalized.contains(it) }) return null

        val amountMatch = AMOUNT_REGEX.find(fullContent)?.groupValues?.get(1) ?: return null
        val cents = MoneyUtils.parseToCents(amountMatch) ?: return null
        if (!SecuritySanitizer.isValidAmountCents(cents)) return null

        val lastFour = LAST_FOUR_REGEX.find(fullContent)?.groupValues?.get(1)
        val rawInstallments = INSTALLMENTS_REGEX.find(fullContent)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val installments = SecuritySanitizer.validateInstallments(rawInstallments)

        val ignoredMerchantWords = setOf(
            "crédito", "credito", "débito", "debito",
            "cartão", "cartao", "limite", "conta", "valor"
        )
        val rawMerchant = MERCHANT_REGEX.findAll(fullContent)
            .map { it.groupValues[1].trim() }
            .firstOrNull { candidate ->
                val lower = candidate.lowercase()
                lower !in ignoredMerchantWords &&
                    ignoredMerchantWords.none { lower == it || lower.startsWith("$it ") }
            }
        val fallbackTitle = safeTitle.takeIf { it.isNotBlank() && !it.lowercase().contains("compra") }
        val merchant = SecuritySanitizer.sanitizeHeaderField(
            rawMerchant?.takeIf { it.isNotBlank() } ?: fallbackTitle ?: "Compra Automatica",
            maxLength = 40
        )

        val hashInput = "$packageName|${lastFour ?: "NONE"}|$cents|${merchant.trim().lowercase()}"

        return ParsedBankExpense(
            amount = cents,
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
