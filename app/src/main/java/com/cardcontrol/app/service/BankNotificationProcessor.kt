package com.cardcontrol.app.service

import com.cardcontrol.app.data.local.AppDao
import com.cardcontrol.app.data.local.CreditCardEntity
import com.cardcontrol.app.data.local.HolderEntity
import com.cardcontrol.app.data.repository.CardRepository
import com.cardcontrol.app.data.repository.CardResolution
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Resultado do processamento de uma notificação recebida pelo [BankNotificationListener]. */
sealed interface AutoCaptureResult {
    data class Registered(
        val transactionId: Long,
        val expense: ParsedBankExpense,
        val card: CreditCardEntity,
        val holder: HolderEntity
    ) : AutoCaptureResult

    data class Ignored(val reason: IgnoreReason) : AutoCaptureResult
}

enum class IgnoreReason {
    UNSUPPORTED_PACKAGE,
    NOT_A_PURCHASE_OR_AMBIGUOUS,
    DUPLICATE,
    NO_MATCHING_CARD,
    AMBIGUOUS_CARD,
    NO_HOLDER
}

/**
 * Caminho único entre a notificação recebida e o lançamento da despesa:
 * [BankNotificationParser] -> deduplicação -> associação de cartão/titular -> [CardRepository.registerExpense].
 *
 * Não depende de APIs Android, para que o fluxo integrado possa ser testado em JVM.
 */
class BankNotificationProcessor(
    private val dao: AppDao,
    private val repository: CardRepository = CardRepository(dao)
) {

    suspend fun process(
        packageName: String?,
        title: String?,
        text: String?,
        postTime: Long
    ): AutoCaptureResult {
        if (!BankNotificationParser.isSupportedPackage(packageName)) {
            return AutoCaptureResult.Ignored(IgnoreReason.UNSUPPORTED_PACKAGE)
        }
        val parsed = BankNotificationParser.parse(packageName, title, text)
            ?: return AutoCaptureResult.Ignored(IgnoreReason.NOT_A_PURCHASE_OR_AMBIGUOUS)

        // Serializa verificação + inserção para que notificações repetidas simultâneas
        // não passem ambas pela checagem de duplicidade antes de gravar.
        return CAPTURE_LOCK.withLock {
            if (repository.isDuplicateNotification(parsed.dedupHash, postTime)) {
                return@withLock AutoCaptureResult.Ignored(IgnoreReason.DUPLICATE)
            }

            val card = when (val resolution = repository.resolveCard(packageName!!, parsed.lastFourDigits)) {
                is CardResolution.Found -> resolution.card
                CardResolution.NoMatch -> return@withLock AutoCaptureResult.Ignored(IgnoreReason.NO_MATCHING_CARD)
                CardResolution.Ambiguous -> return@withLock AutoCaptureResult.Ignored(IgnoreReason.AMBIGUOUS_CARD)
            }

            val holderId = card.defaultHolderId
                ?: return@withLock AutoCaptureResult.Ignored(IgnoreReason.NO_HOLDER)
            val holder = dao.getHolderById(holderId)
                ?: return@withLock AutoCaptureResult.Ignored(IgnoreReason.NO_HOLDER)

            val transactionId = repository.registerExpense(
                cardId = card.id,
                holderId = holder.id,
                merchant = parsed.merchant,
                totalAmount = parsed.amount,
                installmentsCount = parsed.installments,
                purchaseTimestamp = postTime,
                isAutoCaptured = true,
                dedupHash = parsed.dedupHash
            )
            if (transactionId == -1L) return@withLock AutoCaptureResult.Ignored(IgnoreReason.DUPLICATE)
            AutoCaptureResult.Registered(transactionId, parsed, card, holder)
        }
    }

    private companion object {
        val CAPTURE_LOCK = Mutex()
    }
}
