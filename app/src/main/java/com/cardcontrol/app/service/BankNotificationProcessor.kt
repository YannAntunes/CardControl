package com.cardcontrol.app.service

import com.cardcontrol.app.data.local.AppDao
import com.cardcontrol.app.data.local.CreditCardEntity
import com.cardcontrol.app.data.local.HolderEntity
import com.cardcontrol.app.data.repository.CardRepository
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

            val card = when (val resolution = resolveCard(parsed.lastFourDigits)) {
                is CardResolution.Found -> resolution.card
                is CardResolution.Missing -> return@withLock AutoCaptureResult.Ignored(resolution.reason)
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
            AutoCaptureResult.Registered(transactionId, parsed, card, holder)
        }
    }

    private sealed interface CardResolution {
        data class Found(val card: CreditCardEntity) : CardResolution
        data class Missing(val reason: IgnoreReason) : CardResolution
    }

    /**
     * Com final informado, exige cartão cadastrado com o mesmo final (sem fallback para outro cartão).
     * Sem final, só associa se houver exatamente um cartão cadastrado; caso contrário é ambíguo.
     */
    private suspend fun resolveCard(lastFour: String?): CardResolution {
        if (lastFour != null) {
            val card = dao.getCardByLastFour(lastFour)
                ?: return CardResolution.Missing(IgnoreReason.NO_MATCHING_CARD)
            return CardResolution.Found(card)
        }
        if (dao.countCards() != 1) return CardResolution.Missing(IgnoreReason.AMBIGUOUS_CARD)
        val single = dao.getFirstCard() ?: return CardResolution.Missing(IgnoreReason.NO_MATCHING_CARD)
        return CardResolution.Found(single)
    }

    private companion object {
        val CAPTURE_LOCK = Mutex()
    }
}
