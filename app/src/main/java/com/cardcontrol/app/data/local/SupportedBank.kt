package com.cardcontrol.app.data.local

import java.text.Normalizer
import java.util.Locale

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
        private val PT_BR = Locale("pt", "BR")

        fun fromId(id: String?): SupportedBank {
            if (id == null) return OTHER
            val normalized = id.trim().lowercase(PT_BR)
            return entries.firstOrNull { it.id == normalized } ?: OTHER
        }

        fun fromPackageName(packageName: String?): SupportedBank {
            if (packageName == null) return OTHER
            return entries.firstOrNull { it.packageNames.contains(packageName) } ?: OTHER
        }

        /**
         * Infere o banco com base no apelido/nome do cartão.
         * Política de segurança estrita:
         * - Se nenhum banco for identificado ou se mais de um banco for encontrado no nome (ambiguidade),
         *   retorna [OTHER]. Nunca associa automaticamente de forma insegura.
         */
        fun inferFromCardName(cardName: String): SupportedBank {
            val normalized = Normalizer.normalize(cardName.lowercase(PT_BR), Normalizer.Form.NFD)
                .replace(Regex("""\p{Mn}+"""), "")
            val tokens = normalized.split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }.toSet()

            val matches = mutableListOf<SupportedBank>()
            if (tokens.any { it in setOf("nu", "nubank") }) matches.add(NUBANK)
            if (tokens.any { it in setOf("inter") }) matches.add(INTER)
            if (tokens.any { it in setOf("itau", "itaucard") }) matches.add(ITAU)
            if (tokens.any { it in setOf("c6", "c6bank") }) matches.add(C6)
            if (tokens.any { it in setOf("santander") }) matches.add(SANTANDER)
            if (tokens.any { it in setOf("bradesco") }) matches.add(BRADESCO)
            if (tokens.any { it in setOf("caixa") }) matches.add(CAIXA)
            if (tokens.any { it in setOf("picpay") }) matches.add(PICPAY)
            if (tokens.any { it in setOf("mercado", "mercadopago") }) matches.add(MERCADO_PAGO)

            return if (matches.size == 1) matches.first() else OTHER
        }
    }
}
