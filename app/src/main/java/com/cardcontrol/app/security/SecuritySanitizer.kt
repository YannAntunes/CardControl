package com.cardcontrol.app.security

object SecuritySanitizer {
    private const val MAX_NOTIFICATION_LENGTH = 1000
    private val SAFE_FILENAME_REGEX = Regex("""[^a-zA-Z0-9_\-]""")
    private val CRLF_REGEX = Regex("""[\r\n\t]+""")

    fun boundNotificationInput(raw: String): String = raw.take(MAX_NOTIFICATION_LENGTH).trim()

    fun sanitizeHeaderField(input: String, maxLength: Int = 80): String {
        return input.replace(CRLF_REGEX, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(maxLength)
    }

    fun sanitizeFileNameSegment(input: String): String {
        val cleaned = input.trim()
            .replace(Regex("""\s+"""), "_")
            .replace(SAFE_FILENAME_REGEX, "")
            .take(40)
        return cleaned.ifBlank { "Titular" }
    }

    fun isValidAmountCents(cents: Long): Boolean = cents in 1L..100_000_000L

    fun validateFinancialAmount(amountCents: Long): Long {
        require(isValidAmountCents(amountCents)) {
            "Valor financeiro fora dos limites seguros."
        }
        return amountCents
    }

    fun validateLimitAmount(limitCents: Long): Long = limitCents.coerceIn(0L, 100_000_000L)

    fun validateInstallments(count: Int): Int = count.coerceIn(1, 72)
}
