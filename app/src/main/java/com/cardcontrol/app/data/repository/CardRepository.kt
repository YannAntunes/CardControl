package com.cardcontrol.app.data.repository

import com.cardcontrol.app.data.local.*
import com.cardcontrol.app.security.SecuritySanitizer
import com.cardcontrol.app.service.BankNotificationParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    suspend fun addHolder(name: String, whatsapp: String, email: String, subLimit: Long) {
        dao.insertHolder(
            HolderEntity(
                name = SecuritySanitizer.sanitizeHeaderField(name, 50),
                whatsapp = whatsapp.filter { it.isDigit() }.take(15),
                email = email.trim().take(80),
                subLimit = SecuritySanitizer.validateLimitAmount(subLimit)
            )
        )
    }

    suspend fun addCard(
        name: String,
        bank: String = SupportedBank.OTHER.id,
        lastFour: String,
        totalLimit: Long,
        closingDay: Int,
        dueDay: Int,
        defaultHolderId: Long?
    ) {
        val safeBank = SupportedBank.fromId(bank).id
        dao.insertCard(
            CreditCardEntity(
                name = SecuritySanitizer.sanitizeHeaderField(name, 40),
                bank = safeBank,
                lastFourDigits = lastFour.filter { it.isDigit() }.takeLast(4),
                totalLimit = SecuritySanitizer.validateLimitAmount(totalLimit),
                closingDay = closingDay.coerceIn(1, 28),
                dueDay = dueDay.coerceIn(1, 28),
                defaultHolderId = defaultHolderId
            )
        )
    }

    suspend fun updateCard(
        cardId: Long,
        name: String,
        bank: String = SupportedBank.OTHER.id,
        lastFour: String,
        totalLimit: Long,
        closingDay: Int,
        dueDay: Int,
        defaultHolderId: Long?
    ): Boolean {
        val existing = dao.getCardById(cardId) ?: return false
        val safeName = SecuritySanitizer.sanitizeHeaderField(name, 40)
        val digits = lastFour.filter { it.isDigit() }.takeLast(4)
        if (safeName.isBlank() || digits.length != 4) return false
        if (defaultHolderId != null && dao.getHolderById(defaultHolderId) == null) return false

        val safeBank = SupportedBank.fromId(bank).id
        val updated = existing.copy(
            name = safeName,
            bank = safeBank,
            lastFourDigits = digits,
            totalLimit = SecuritySanitizer.validateLimitAmount(totalLimit),
            closingDay = closingDay.coerceIn(1, 28),
            dueDay = dueDay.coerceIn(1, 28),
            defaultHolderId = defaultHolderId
        )
        return dao.updateCard(updated) > 0
    }

    suspend fun deleteCardIfUnused(cardId: Long): Boolean {
        // A remoção é atômica e só acontece se não houver histórico financeiro vinculado.
        return dao.deleteCardIfUnused(cardId) > 0
    }

    suspend fun isDuplicateNotification(dedupHash: String, timestamp: Long): Boolean {
        if (dedupHash.isBlank()) return false
        return dao.countRecentDuplicate(dedupHash, timestamp - 120_000L) > 0
    }

    suspend fun resolveCardForNotification(packageName: String, lastFourDigits: String?): CreditCardEntity? {
        val cards = dao.getAllCardsSnapshot()
        if (cards.isEmpty()) return null

        val notifiedBank = SupportedBank.fromPackageName(packageName)

        fun belongsToNotifiedBank(card: CreditCardEntity): Boolean {
            if (notifiedBank == SupportedBank.OTHER) return false

            // 1. Prioridade absoluta: campo explícito do banco
            val cardBank = SupportedBank.fromId(card.bank)
            if (cardBank != SupportedBank.OTHER) {
                return cardBank == notifiedBank
            }

            // 2. Fallback de compatibilidade retroativa para cartões legados sem banco explícito
            return SupportedBank.inferFromCardName(card.name) == notifiedBank
        }

        if (lastFourDigits != null) {
            val matchingDigits = cards.filter { it.lastFourDigits == lastFourDigits }
            if (matchingDigits.isEmpty()) return null
            // Mesmo um único cartão deve corresponder ao banco que originou a notificação.
            return matchingDigits.filter(::belongsToNotifiedBank).singleOrNull()
        }

        // Sem os quatro dígitos, só resolvemos quando existe um único cartão e ele é compatível
        // com o banco de origem. Havendo ambiguidade, não lançamos uma compra no titular errado.
        val onlyCard = cards.singleOrNull() ?: return null
        return onlyCard.takeIf(::belongsToNotifiedBank)
    }

    suspend fun processBankNotification(
        packageName: String,
        title: String,
        text: String,
        timestamp: Long
    ): Pair<Long, CreditCardEntity?> {
        val parsed = BankNotificationParser.parse(packageName, title, text, timestamp)
            ?: return Pair(-1L, null)

        val matchedCard = resolveCardForNotification(packageName, parsed.lastFourDigits)
            ?: return Pair(-1L, null)

        val holderId = matchedCard.defaultHolderId
            ?: return Pair(-1L, null)

        val holder = dao.getHolderById(holderId)
            ?: return Pair(-1L, null)

        val txId = registerExpense(
            cardId = matchedCard.id,
            holderId = holder.id,
            merchant = parsed.merchant,
            totalAmount = parsed.amount,
            installmentsCount = parsed.installments,
            purchaseTimestamp = timestamp,
            isAutoCaptured = true,
            dedupHash = parsed.dedupHash
        )

        return if (txId != -1L) Pair(txId, matchedCard) else Pair(-1L, null)
    }

    suspend fun registerExpense(
        cardId: Long,
        holderId: Long,
        merchant: String,
        totalAmount: Long,
        installmentsCount: Int,
        purchaseTimestamp: Long = System.currentTimeMillis(),
        isAutoCaptured: Boolean = false,
        dedupHash: String = ""
    ): Long = expenseMutex.withLock {
        if (isAutoCaptured && dedupHash.isNotBlank()) {
            if (dao.countRecentDuplicate(dedupHash, purchaseTimestamp - 120_000L) > 0) {
                return@withLock -1L
            }
        }
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

        return@withLock dao.insertTransactionWithInstallments(tx) { txId ->
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
        amountPaid: Long,
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
        private val expenseMutex = Mutex()

        fun calculateFirstInstallmentMonth(purchaseDate: LocalDate, closingDay: Int): LocalDate {
            val baseMonth = LocalDate.of(purchaseDate.year, purchaseDate.monthValue, 1)
            return if (purchaseDate.dayOfMonth >= closingDay) baseMonth.plusMonths(1) else baseMonth
        }

        fun splitInstallmentAmounts(totalAmount: Long, installments: Int): List<Long> {
            val count = installments.coerceIn(1, 48)
            val base = totalAmount / count
            val remainder = (totalAmount % count).toInt()
            return List(count) { index -> if (index < remainder) base + 1L else base }
        }
    }
}
