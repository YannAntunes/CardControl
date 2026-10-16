package com.cardcontrol.app.data.repository

import com.cardcontrol.app.data.local.*
import com.cardcontrol.app.security.SecuritySanitizer
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CardRepository(private val dao: AppDao) {
    val holders = dao.observeHolders()
    val cards = dao.observeCards()
    val allInstallments = dao.observeAllInstallments()
    val allPayments = dao.observeAllPayments()

    fun observeInstallmentsForMonth(month: Int, year: Int) = dao.observeInstallmentsByPeriod(month, year)
    fun observePaymentsForMonth(month: Int, year: Int) = dao.observePaymentsByPeriod(month, year)

    suspend fun addHolder(name: String, whatsapp: String, email: String, subLimit: Double) {
        dao.insertHolder(
            HolderEntity(
                name = SecuritySanitizer.sanitizeHeaderField(name, 50),
                whatsapp = whatsapp.filter { it.isDigit() }.take(15),
                email = email.trim().take(80),
                subLimit = subLimit.coerceAtLeast(0.0)
            )
        )
    }

    suspend fun addCard(
        name: String,
        lastFour: String,
        totalLimit: Double,
        closingDay: Int,
        dueDay: Int,
        defaultHolderId: Long?,
        issuerBank: String? = null
    ) {
        dao.insertCard(
            CreditCardEntity(
                name = SecuritySanitizer.sanitizeHeaderField(name, 40),
                lastFourDigits = lastFour.filter { it.isDigit() }.takeLast(4),
                totalLimit = totalLimit.coerceAtLeast(0.0),
                closingDay = closingDay.coerceIn(1, 28),
                dueDay = dueDay.coerceIn(1, 28),
                defaultHolderId = defaultHolderId,
                issuerBank = issuerBank?.takeIf { it in com.cardcontrol.app.service.BankNotificationParser.SUPPORTED_ISSUERS }
            )
        )
    }

    suspend fun isDuplicateNotification(dedupHash: String, timestamp: Long): Boolean {
        if (dedupHash.isBlank()) return false
        return dao.countRecentDuplicate(dedupHash, timestamp - 120_000L) > 0
    }

    suspend fun registerExpense(
        cardId: Long,
        holderId: Long,
        merchant: String,
        totalAmount: Double,
        installmentsCount: Int,
        purchaseTimestamp: Long = System.currentTimeMillis(),
        isAutoCaptured: Boolean = false,
        dedupHash: String = ""
    ): Long {
        val card = dao.getCardById(cardId) ?: throw IllegalArgumentException("Cartao nao encontrado")
        val validAmount = SecuritySanitizer.validateFinancialAmount(totalAmount)
        val safeInstallments = SecuritySanitizer.validateInstallments(installmentsCount)
        val safeMerchant = SecuritySanitizer.sanitizeHeaderField(merchant, 40)

        val purchaseDate: LocalDate = Instant.ofEpochMilli(purchaseTimestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val startYearMonth = calculateFirstInstallmentMonth(purchaseDate, card.closingDay)
        val amounts = splitInstallmentAmounts(validAmount, safeInstallments)

        val tx = TransactionEntity(
            cardId = cardId,
            holderId = holderId,
            merchant = safeMerchant,
            totalAmount = validAmount,
            installmentsCount = safeInstallments,
            purchaseTimestamp = purchaseTimestamp,
            isAutoCaptured = isAutoCaptured,
            dedupHash = dedupHash
        )

        return dao.insertTransactionWithInstallments(tx) { txId ->
            amounts.mapIndexed { index, parcelAmount ->
                val targetDate = startYearMonth.plusMonths(index.toLong())
                InstallmentEntity(
                    transactionId = txId,
                    holderId = holderId,
                    cardId = cardId,
                    merchantSnapshot = safeMerchant,
                    installmentNumber = index + 1,
                    totalInstallments = safeInstallments,
                    amount = parcelAmount,
                    refMonth = targetDate.monthValue,
                    refYear = targetDate.year,
                    purchaseTimestamp = purchaseTimestamp
                )
            }
        }
    }

    suspend fun registerPayment(
        holderId: Long,
        cardId: Long?,
        amountPaid: Double,
        refMonth: Int,
        refYear: Int,
        notes: String
    ) {
        val validAmount = SecuritySanitizer.validateFinancialAmount(amountPaid)
        dao.insertPayment(
            PaymentEntity(
                holderId = holderId,
                cardId = cardId,
                amountPaid = validAmount,
                refMonth = refMonth,
                refYear = refYear,
                notes = SecuritySanitizer.sanitizeHeaderField(notes, 80)
            )
        )
    }

    companion object {
        fun calculateFirstInstallmentMonth(purchaseDate: LocalDate, closingDay: Int): LocalDate {
            val baseMonth = LocalDate.of(purchaseDate.year, purchaseDate.monthValue, 1)
            return if (purchaseDate.dayOfMonth >= closingDay) baseMonth.plusMonths(1) else baseMonth
        }

        fun splitInstallmentAmounts(totalAmount: Double, count: Int): List<Double> {
            val safeCount = SecuritySanitizer.validateInstallments(count)
            val totalBd = BigDecimal.valueOf(totalAmount).setScale(2, RoundingMode.HALF_UP)
            val countBd = BigDecimal.valueOf(safeCount.toLong())
            val baseValue = totalBd.divide(countBd, 2, RoundingMode.DOWN)
            val remainder = totalBd.subtract(baseValue.multiply(countBd))

            return List(safeCount) { index ->
                if (index == 0) baseValue.add(remainder).toDouble() else baseValue.toDouble()
            }
        }
    }
}
