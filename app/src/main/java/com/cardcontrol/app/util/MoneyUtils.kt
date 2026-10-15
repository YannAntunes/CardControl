package com.cardcontrol.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object MoneyUtils {
    private val ptBrLocale = Locale("pt", "BR")

    fun parseToCents(raw: String): Long? {
        val clean = raw.replace("R$", "", ignoreCase = true).trim()
        if (clean.isEmpty()) return null
        val normalized = if (clean.contains(",")) {
            clean.replace(".", "").replace(",", ".")
        } else {
            clean
        }
        return try {
            val bd = BigDecimal(normalized)
            if (bd < BigDecimal.ZERO) return null
            bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    fun formatCents(cents: Long): String {
        val bd = BigDecimal.valueOf(cents, 2)
        return NumberFormat.getCurrencyInstance(ptBrLocale).format(bd)
    }

    fun formatDecimal(cents: Long): String {
        return String.format(ptBrLocale, "%.2f", cents / 100.0)
    }
}
