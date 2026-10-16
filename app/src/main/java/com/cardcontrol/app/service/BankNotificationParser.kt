package com.cardcontrol.app.service

import com.cardcontrol.app.security.SecuritySanitizer
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Resultado de uma notificação bancária reconhecida, com segurança, como compra no cartão.
 *
 * [amount] é o valor exato em centavos (contrato de [com.cardcontrol.app.data.repository.CardRepository.registerExpense]);
 * [amountCents] é um alias semântico.
 */
data class ParsedBankExpense(
    val bankName: String,
    val amount: Long,
    val merchant: String,
    val lastFourDigits: String?,
    val installments: Int,
    val dedupHash: String
) {
    val amountCents: Long get() = amount
}

/**
 * Fonte única de parsing de notificações bancárias.
 *
 * Política: na dúvida, não lançar. Qualquer ambiguidade (múltiplos valores, múltiplos finais,
 * parcelas fora do limite, estabelecimento ausente, mensagens de fatura/oferta/estorno)
 * resulta em `null`, e nenhuma despesa é criada automaticamente.
 */
object BankNotificationParser {

    /**
     * Pacotes confirmados na Google Play (HTTP 200 em play.google.com/store/apps/details?id=...).
     * Não adicione pacotes sem confirmar o identificador e sem testes do formato de mensagem.
     */
    private val SUPPORTED_BANKS: Map<String, String> = linkedMapOf(
        "com.nu.production" to "Nubank",
        "br.com.intermedium" to "Inter",
        "com.itau" to "Itaú",
        "com.c6bank.app" to "C6 Bank",
        "com.santander.app" to "Santander",
        "com.bradesco" to "Bradesco",
        "br.com.bradesco.cartoes" to "Bradesco Cartões",
        "br.gov.caixa.cartoes" to "Caixa",
        "com.picpay" to "PicPay",
        "com.mercadopago.wallet" to "Mercado Pago"
    )

    val SUPPORTED_PACKAGES: Set<String> get() = SUPPORTED_BANKS.keys

    const val MAX_INSTALLMENTS = 72
    private val MAX_AMOUNT_CENTS = 100_000_000L // R$ 1.000.000,00

    private val PT_BR = Locale("pt", "BR")

    // Todos os padrões de palavra-chave operam sobre texto minúsculo e sem acentos.
    private val PURCHASE_TRIGGER_REGEX = Regex(
        """compra\s+(?:no\s+(?:credito|debito)\s+)?aprovada""" +
            """|compra\s+de\s+(?:r\$|brl)\s*[0-9.,]+\s+aprovada""" +
            """|compra\s+(?:realizada|efetuada)""" +
            """|transacao\s+(?:no\s+(?:credito|debito)\s+)?aprovada""" +
            """|voce\s+comprou"""
    )

    /** Contextos que nunca são compra, em qualquer posição do texto. */
    private val IGNORED_CONTEXT_REGEX = Regex(
        """\b(?:negada|recusada|nao\s+aprovada|nao\s+autorizada|estorn\w*|cancelad\w*|cancelamento""" +
            """|reembolso|devolucao|devolvid\w*|fatura""" +
            """|aumento\s+de\s+limite|novo\s+limite|limite\s+aumentado|emprestimo|pix|transferencia""" +
            """|boleto|saque|codigo\s+de\s+(?:verificacao|seguranca)|token\s+de\s+seguranca|seu\s+token|senha)\b"""
    )

    /**
     * Cashback/oferta/promoção/desconto só descartam a mensagem quando acompanhados de valor ou percentual
     * ("cashback de R$ 5,00", "10% de desconto"). A palavra isolada pode fazer parte do nome do
     * estabelecimento ("SUPERMERCADO DA OFERTA") ou de um aviso adicional de compra legítima.
     */
    private val PROMOTIONAL_AMOUNT_REGEX = Regex(
        """\b(?:cashback|oferta\w*|promoc\w*|desconto\w*)\b.{0,30}?(?:r\$|brl|%)|[0-9]\s*%"""
    )

    private const val AMOUNT_NUMBER =
        """([0-9]{1,3}(?:\.[0-9]{3})+,[0-9]{2}|[0-9]+,[0-9]{2}|[0-9]{1,3}(?:,[0-9]{3})+\.[0-9]{2}|[0-9]+\.[0-9]{2})(?![0-9])"""

    private val AMOUNT_REGEX = Regex("""(?:R\$|BRL)\s*$AMOUNT_NUMBER""", RegexOption.IGNORE_CASE)

    /** Trechos que mencionam valores que NÃO são o total da compra (valor da parcela, limite, saldo). */
    private val NON_PURCHASE_AMOUNT_REGEX = Regex(
        """(?:\b[0-9]{1,3}\s*(?:x|vezes|parcelas?)\s+de\s+|\blimite\b.{0,25}?|\bsaldo\b.{0,25}?)(?:R\$|BRL)\s*$AMOUNT_NUMBER""",
        RegexOption.IGNORE_CASE
    )

    private val INSTALLMENTS_REGEX = Regex("""\b([0-9]{1,3})\s*(?:x|vezes|parcelas)(?![\p{L}0-9])""", RegexOption.IGNORE_CASE)

    private val CARD_DIGITS_REGEX = Regex(
        """(?:\bfinal|\bterminad[oa]\s+em|\*{2,})\s*:?\s*([0-9]{4})(?![0-9])""",
        RegexOption.IGNORE_CASE
    )

    private val MERCHANT_REGEX = Regex(
        """(?:\bem|\bno|\bna|\bestabelecimento:?|\bloja:?)\s+(?![0-9])([\p{L}\p{N}*&'./\- ]{2,40}?)""" +
            """(?=\s+(?:em\s+[0-9]|para\b|com\b|no\s+cart|cart[aã]o\b|final\b|no\s+valor|de\s+(?:R\$|BRL)|R\$|BRL|[àa]s\s+[0-9]|aprovad|compra\b)""" +
            """|\s*[.,:;!]\s|\s*[.,:;!]?\s*$)""",
        RegexOption.IGNORE_CASE
    )

    private val MERCHANT_FORBIDDEN_WORDS = Regex(
        """\b(?:cartao|compra|credito|debito|valor|aprovad\w*|final|parcela\w*|vezes)\b"""
    )

    fun isSupportedPackage(packageName: String?): Boolean =
        packageName != null && SUPPORTED_BANKS.containsKey(packageName)

    fun bankNameFor(packageName: String): String? = SUPPORTED_BANKS[packageName]

    /** Emissor canônico: os dois pacotes do Bradesco pertencem ao mesmo emissor. */
    fun issuerFor(packageName: String?): String? =
        packageName?.let { SUPPORTED_BANKS[it] }?.let { if (it == "Bradesco Cartões") "Bradesco" else it }

    /** Emissores que podem ser vinculados a um cartão, na ordem de [SUPPORTED_BANKS]. */
    val SUPPORTED_ISSUERS: List<String> get() = SUPPORTED_BANKS.keys.mapNotNull { issuerFor(it) }.distinct()

    private val ISSUER_NAME_KEYWORDS: Map<String, List<String>> = mapOf(
        "Nubank" to listOf("nu", "nubank"),
        "Inter" to listOf("inter"),
        "Itaú" to listOf("itau", "itaucard"),
        "C6 Bank" to listOf("c6", "c6bank"),
        "Santander" to listOf("santander"),
        "Bradesco" to listOf("bradesco"),
        "Caixa" to listOf("caixa"),
        "PicPay" to listOf("picpay"),
        "Mercado Pago" to listOf("mercado", "mercadopago")
    )

    /**
     * Infere o emissor de um cartão legado (sem emissor gravado) pelo apelido, apenas por palavra inteira.
     * Retorna nulo se nenhum ou mais de um emissor for reconhecido: nesse caso o cartão não é confirmado.
     */
    fun issuerFromCardName(cardName: String): String? {
        val tokens = normalize(cardName).split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }.toSet()
        val matches = ISSUER_NAME_KEYWORDS.filterValues { keywords -> keywords.any { it in tokens } }.keys
        return matches.singleOrNull()
    }

    @Suppress("UNUSED_PARAMETER")
    fun parse(packageName: String?, title: String?, text: String?, timestamp: Long = 0L): ParsedBankExpense? {
        if (packageName == null) return null
        val bankName = SUPPORTED_BANKS[packageName] ?: return null

        val safeTitle = SecuritySanitizer.boundNotificationInput(title.orEmpty())
        val safeText = SecuritySanitizer.boundNotificationInput(text.orEmpty())
        val content = SecuritySanitizer.sanitizeHeaderField(
            listOf(safeTitle, safeText).filter { it.isNotBlank() }.joinToString(" "),
            maxLength = 2_000
        )
        if (content.isBlank()) return null

        val normalized = normalize(content)
        if (IGNORED_CONTEXT_REGEX.containsMatchIn(normalized)) return null
        if (PROMOTIONAL_AMOUNT_REGEX.containsMatchIn(normalized)) return null
        if (!PURCHASE_TRIGGER_REGEX.containsMatchIn(normalized)) return null

        val amountCents = extractPurchaseAmountCents(content) ?: return null
        val installments = extractInstallments(content) ?: return null
        val lastFour = extractLastFour(content) ?: return null
        val merchant = extractMerchant(content) ?: return null

        val hashInput = "$packageName|${lastFour.value ?: "----"}|$amountCents|$installments|${merchant.lowercase(PT_BR)}"

        return ParsedBankExpense(
            bankName = bankName,
            amount = amountCents,
            merchant = merchant,
            lastFourDigits = lastFour.value,
            installments = installments,
            dedupHash = sha256(hashInput)
        )
    }

    /** Converte "1.850,00", "1850,00", "1,850.00" ou "1850.00" para centavos exatos. */
    internal fun parseAmountToCents(raw: String): Long? {
        val normalized = when {
            raw.contains(',') && raw.contains('.') ->
                if (raw.lastIndexOf(',') > raw.lastIndexOf('.')) raw.replace(".", "").replace(',', '.')
                else raw.replace(",", "")
            raw.contains(',') -> raw.replace(',', '.')
            else -> raw
        }
        return try {
            BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun extractPurchaseAmountCents(content: String): Long? {
        val purchaseOnly = NON_PURCHASE_AMOUNT_REGEX.replace(content, " ")
        val candidates = AMOUNT_REGEX.findAll(purchaseOnly)
            .mapNotNull { parseAmountToCents(it.groupValues[1]) }
            .toSet()
        // Nenhum ou mais de um valor distinto: ambíguo, não lançar.
        val cents = candidates.singleOrNull() ?: return null
        return cents.takeIf { it in 1..MAX_AMOUNT_CENTS }
    }

    private fun extractInstallments(content: String): Int? {
        val found = INSTALLMENTS_REGEX.findAll(content)
            .map { it.groupValues[1].toInt() }
            .toSet()
        if (found.isEmpty()) return 1
        val value = found.singleOrNull() ?: return null
        return value.takeIf { it in 1..MAX_INSTALLMENTS }
    }

    /** Wrapper para diferenciar "sem final" (válido) de "finais conflitantes" (ambíguo). */
    private data class LastFour(val value: String?)

    private fun extractLastFour(content: String): LastFour? {
        val found = CARD_DIGITS_REGEX.findAll(content).map { it.groupValues[1] }.toSet()
        return when (found.size) {
            0 -> LastFour(null)
            1 -> LastFour(found.first())
            else -> null
        }
    }

    private fun extractMerchant(content: String): String? {
        for (match in MERCHANT_REGEX.findAll(content)) {
            val candidate = SecuritySanitizer.sanitizeHeaderField(
                match.groupValues[1].trim().trimEnd('.', '-', '/'),
                maxLength = 40
            )
            if (candidate.length < 2) continue
            if (candidate.none { it.isLetter() }) continue
            if (MERCHANT_FORBIDDEN_WORDS.containsMatchIn(normalize(candidate))) continue
            return candidate
        }
        return null
    }

    private fun normalize(input: String): String =
        Normalizer.normalize(input.lowercase(PT_BR), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }
}
