package com.cardcontrol.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cardcontrol.app.data.local.*
import com.cardcontrol.app.data.repository.CardRepository
import com.cardcontrol.app.data.security.CredentialStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HolderSummaryUi(
    val holder: HolderEntity,
    val monthlyInstallments: List<InstallmentEntity>,
    val monthlySpent: Long,
    val monthlyPaid: Long,
    val monthlyNetDue: Long,
    val globalCommitted: Long,
    val availableSubLimit: Long
)

data class CardSummaryUi(
    val card: CreditCardEntity,
    val globalCommitted: Long,
    val availableLimit: Long
)

data class DashboardUiState(
    val selectedMonth: Int = LocalDate.now().monthValue,
    val selectedYear: Int = LocalDate.now().year,
    val holders: List<HolderEntity> = emptyList(),
    val cards: List<CreditCardEntity> = emptyList(),
    val holderSummaries: List<HolderSummaryUi> = emptyList(),
    val cardSummaries: List<CardSummaryUi> = emptyList(),
    val totalMonthInvoice: Long = 0L,
    val totalMonthPaid: Long = 0L
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val uiMessages = _uiMessages.asSharedFlow()
    private val dao = AppDatabase.getInstance(application).appDao()
    private val repository = CardRepository(dao)
    val credentialStore = CredentialStore(application)

    private val selectedPeriod = MutableStateFlow(LocalDate.now().withDayOfMonth(1))

    private val periodInstallments = selectedPeriod.flatMapLatest { date ->
        repository.observeInstallmentsForMonth(date.monthValue, date.year)
    }

    private val periodPayments = selectedPeriod.flatMapLatest { date ->
        repository.observePaymentsForMonth(date.monthValue, date.year)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        selectedPeriod,
        repository.holders,
        repository.cards,
        periodInstallments,
        periodPayments,
        repository.allInstallments,
        repository.allPayments
    ) { args: Array<Any?> ->
        val date = args[0] as LocalDate
        @Suppress("UNCHECKED_CAST")
        val holders = args[1] as List<HolderEntity>
        @Suppress("UNCHECKED_CAST")
        val cards = args[2] as List<CreditCardEntity>
        @Suppress("UNCHECKED_CAST")
        val monthInst = args[3] as List<InstallmentEntity>
        @Suppress("UNCHECKED_CAST")
        val monthPay = args[4] as List<PaymentEntity>
        @Suppress("UNCHECKED_CAST")
        val allInst = args[5] as List<InstallmentEntity>
        @Suppress("UNCHECKED_CAST")
        val allPay = args[6] as List<PaymentEntity>

        val holderSummaries = holders.map { h ->
            val hMonthInst = monthInst.filter { it.holderId == h.id }
            val mSpent = hMonthInst.sumOf { it.amount }
            val mPaid = monthPay.filter { it.holderId == h.id }.sumOf { it.amountPaid }
            val gSpent = allInst.filter { it.holderId == h.id }.sumOf { it.amount }
            val gPaid = allPay.filter { it.holderId == h.id }.sumOf { it.amountPaid }
            val gCommitted = (gSpent - gPaid).coerceAtLeast(0L)

            HolderSummaryUi(
                holder = h,
                monthlyInstallments = hMonthInst,
                monthlySpent = mSpent,
                monthlyPaid = mPaid,
                monthlyNetDue = (mSpent - mPaid).coerceAtLeast(0L),
                globalCommitted = gCommitted,
                availableSubLimit = h.subLimit - gCommitted
            )
        }

        val cardSummaries = cards.map { c ->
            val cSpent = allInst.filter { it.cardId == c.id }.sumOf { it.amount }
            val cPaid = allPay.filter { it.cardId == c.id }.sumOf { it.amountPaid }
            val cCommitted = (cSpent - cPaid).coerceAtLeast(0L)
            CardSummaryUi(
                card = c,
                globalCommitted = cCommitted,
                availableLimit = c.totalLimit - cCommitted
            )
        }

        DashboardUiState(
            selectedMonth = date.monthValue,
            selectedYear = date.year,
            holders = holders,
            cards = cards,
            holderSummaries = holderSummaries,
            cardSummaries = cardSummaries,
            totalMonthInvoice = monthInst.sumOf { it.amount },
            totalMonthPaid = monthPay.sumOf { it.amountPaid }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun changeMonth(delta: Long) { selectedPeriod.value = selectedPeriod.value.plusMonths(delta) }
    fun addHolder(name: String, whatsapp: String, email: String, subLimit: Long) {
        viewModelScope.launch { repository.addHolder(name, whatsapp, email, subLimit) }
    }
    fun addCard(name: String, bank: String, lastFour: String, limit: Long, closing: Int, due: Int, defaultHolderId: Long?) {
        viewModelScope.launch {
            repository.addCard(name, bank, lastFour, limit, closing, due, defaultHolderId)
            _uiMessages.emit("Cartão cadastrado.")
        }
    }
    fun updateCard(id: Long, name: String, bank: String, lastFour: String, limit: Long, closing: Int, due: Int, defaultHolderId: Long?) {
        viewModelScope.launch {
            val updated = repository.updateCard(id, name, bank, lastFour, limit, closing, due, defaultHolderId)
            _uiMessages.emit(
                if (updated) "Alterações do cartão salvas."
                else "Não foi possível salvar. Confira os dados e a pessoa vinculada."
            )
        }
    }
    fun deleteCard(id: Long) {
        viewModelScope.launch {
            val deleted = repository.deleteCardIfUnused(id)
            _uiMessages.emit(
                if (deleted) "Cartão excluído."
                else "Este cartão possui compras ou pagamentos vinculados. Para preservar o histórico, não é possível excluí-lo."
            )
        }
    }
    fun addExpense(cardId: Long, holderId: Long, merchant: String, amount: Long, installments: Int) {
        viewModelScope.launch { repository.registerExpense(cardId, holderId, merchant, amount, installments) }
    }
    fun addPayment(holderId: Long, cardId: Long?, amount: Long, notes: String) {
        val period = selectedPeriod.value
        viewModelScope.launch { repository.registerPayment(holderId, cardId, amount, period.monthValue, period.year, notes) }
    }
}
