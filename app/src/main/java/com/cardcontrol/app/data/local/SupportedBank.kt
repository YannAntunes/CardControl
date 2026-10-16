package com.cardcontrol.app.data.local

enum class SupportedBank(
    val id: String,
    val displayName: String,
    val packageNames: Set<String>
) {
    NUBANK("nubank", "Nubank", setOf("com.nu.production")),
    INTER("inter", "Banco Inter", setOf("br.com.intermedium")),
    ITAU("itau", "Itaú", setOf("com.itau", "com.itaucard.activity")),
    C6("c6", "C6 Bank", setOf("com.c6bank.app")),
    SANTANDER("santander", "Santander", setOf("com.santander.app", "br.com.santander.way")),
    BRADESCO("bradesco", "Bradesco", setOf("com.bradesco", "br.com.bradesco.cartoes")),
    CAIXA("caixa", "Caixa Econômica", setOf("br.gov.caixa.cartoes")),
    PICPAY("picpay", "PicPay", setOf("com.picpay")),
    MERCADO_PAGO("mercadopago", "Mercado Pago", setOf("com.mercadopago.wallet")),
    OTHER("other", "Outro banco", emptySet());

    companion object {
        fun fromId(id: String?): SupportedBank {
            if (id == null) return OTHER
            val normalized = id.trim().lowercase()
            return entries.firstOrNull { it.id == normalized } ?: OTHER
        }

        fun fromPackageName(packageName: String): SupportedBank {
            return entries.firstOrNull { it.packageNames.contains(packageName) } ?: OTHER
        }

        fun inferFromCardName(cardName: String): SupportedBank {
            val normalized = cardName.lowercase()
            return when {
                normalized.contains("nubank") || normalized.contains("nu ") -> NUBANK
                normalized.contains("inter") -> INTER
                normalized.contains("itau") || normalized.contains("itaú") -> ITAU
                normalized.contains("c6") -> C6
                normalized.contains("santander") -> SANTANDER
                normalized.contains("bradesco") -> BRADESCO
                normalized.contains("caixa") -> CAIXA
                normalized.contains("picpay") -> PICPAY
                normalized.contains("mercado") -> MERCADO_PAGO
                else -> OTHER
            }
        }
    }
}
