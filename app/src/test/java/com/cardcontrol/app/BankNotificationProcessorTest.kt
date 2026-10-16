package com.cardcontrol.app

import com.cardcontrol.app.data.local.AppDao
import com.cardcontrol.app.data.local.CreditCardEntity
import com.cardcontrol.app.data.local.HolderEntity
import com.cardcontrol.app.data.local.InstallmentEntity
import com.cardcontrol.app.data.local.PaymentEntity
import com.cardcontrol.app.data.local.TransactionEntity
import com.cardcontrol.app.service.AutoCaptureResult
import com.cardcontrol.app.service.BankNotificationProcessor
import com.cardcontrol.app.service.IgnoreReason
import com.cardcontrol.app.data.local.SupportedBank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Testa o mesmo caminho que o BankNotificationListener executa ao receber uma notificação:
 * BankNotificationProcessor -> BankNotificationParser -> CardRepository.registerExpense -> AppDao.
 */
class BankNotificationProcessorTest {

    private val postTime = 1_760_000_000_000L

    private val nubankText = "Compra de R$ 45,90 APROVADA em PADARIA CENTRAL para o cartão com final 4321."

    private fun daoWith(vararg cards: CreditCardEntity): InMemoryDao {
        val dao = InMemoryDao()
        dao.holders += HolderEntity(id = 1, name = "Ana", whatsapp = "11999999999")
        dao.cards += cards
        return dao
    }

    private val cardAna = CreditCardEntity(id = 10, name = "Nu Ana", bank = SupportedBank.NUBANK.id, lastFourDigits = "4321", totalLimit = 500_000L, closingDay = 5, dueDay = 12, defaultHolderId = 1)
    private val cardItau = CreditCardEntity(id = 20, name = "Itaú Ana", bank = SupportedBank.ITAU.id, lastFourDigits = "9988", totalLimit = 900_000L, closingDay = 5, dueDay = 12, defaultHolderId = 1)

    @Test
    fun `finais iguais em bancos diferentes sao associados pelo banco da notificacao`() = runBlocking {
        val nu = cardAna.copy(id = 11, lastFourDigits = "1111")
        val itau = cardItau.copy(id = 21, lastFourDigits = "1111")
        val dao = daoWith(nu, itau)
        val result = BankNotificationProcessor(dao).process(
            "com.itau", "Itaú", "Compra aprovada no valor de R$ 80,00 em MERCADO SUL cartão final 1111.", postTime
        )
        assertTrue(result is AutoCaptureResult.Registered)
        assertEquals(21L, dao.transactions.single().cardId)
    }

    @Test
    fun `dois cartoes do mesmo banco com o mesmo final sao ambiguos e nao gravam`() = runBlocking {
        val dao = daoWith(cardAna, cardAna.copy(id = 12, name = "Nu Ana 2"))
        val result = BankNotificationProcessor(dao).process("com.nu.production", "Compra aprovada", nubankText, postTime)
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.AMBIGUOUS_CARD), result)
        assertTrue(dao.transactions.isEmpty())
        assertTrue(dao.installments.isEmpty())
    }

    @Test
    fun `unico cartao com o final mas de outro emissor nao recebe a compra`() = runBlocking {
        // Notificação do Nubank com final 4321; o único cartão 4321 é do Itaú.
        val dao = daoWith(cardAna.copy(bank = SupportedBank.ITAU.id))
        val result = BankNotificationProcessor(dao).process("com.nu.production", "Compra aprovada", nubankText, postTime)
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.AMBIGUOUS_CARD), result)
        assertTrue(dao.transactions.isEmpty())
    }

    @Test
    fun `cartao sem emissor e sem banco no apelido nao e associado por notificacao`() = runBlocking {
        val dao = daoWith(cardAna.copy(name = "Cartao Ana", bank = SupportedBank.OTHER.id))
        val result = BankNotificationProcessor(dao).process("com.nu.production", "Compra aprovada", nubankText, postTime)
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.AMBIGUOUS_CARD), result)
        assertTrue(dao.transactions.isEmpty())
    }

    @Test
    fun `cartao legado sem emissor mas com banco no apelido e confirmado`() = runBlocking {
        val dao = daoWith(cardAna.copy(name = "Meu Nubank", bank = SupportedBank.OTHER.id))
        val result = BankNotificationProcessor(dao).process("com.nu.production", "Compra aprovada", nubankText, postTime)
        assertTrue(result is AutoCaptureResult.Registered)
    }

    @Test
    fun `cartao legado com apelido de outro banco nao recebe a compra`() = runBlocking {
        val dao = daoWith(cardAna.copy(name = "Meu Itau", bank = SupportedBank.OTHER.id))
        val result = BankNotificationProcessor(dao).process("com.nu.production", "Compra aprovada", nubankText, postTime)
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.AMBIGUOUS_CARD), result)
        assertTrue(dao.transactions.isEmpty())
    }

    @Test
    fun `unico cartao sem final de outro emissor nao recebe a compra`() = runBlocking {
        val dao = daoWith(cardAna.copy(bank = SupportedBank.ITAU.id))
        val result = BankNotificationProcessor(dao).process(
            "com.nu.production", "Compra aprovada", "Compra aprovada de R$ 25,00 em CAFE DO PONTO.", postTime
        )
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.AMBIGUOUS_CARD), result)
        assertTrue(dao.transactions.isEmpty())
    }

    @Test
    fun `mesma notificacao submetida ao mesmo tempo por duas instancias grava uma unica serie`() = runBlocking {
        val dao = daoWith(cardAna)
        val text = "Compra aprovada de R$ 300,00 em LOJA ALFA em 3x cartão final 4321."
        val results = withContext(Dispatchers.Default) {
            val gate = CompletableDeferred<Unit>()
            val jobs = listOf(BankNotificationProcessor(dao), BankNotificationProcessor(dao)).map { processor ->
                async {
                    gate.await()
                    processor.process("com.nu.production", "Compra aprovada", text, postTime)
                }
            }
            gate.complete(Unit)
            jobs.awaitAll()
        }
        assertEquals(1, results.count { it is AutoCaptureResult.Registered })
        assertEquals(1, results.count { it == AutoCaptureResult.Ignored(IgnoreReason.DUPLICATE) })
        assertEquals(1, dao.transactions.size)
        assertEquals(3, dao.installments.size)
        assertTrue(dao.installments.all { it.transactionId == dao.transactions.single().id })
    }

    @Test
    fun `notificacao recebida gera lancamento com banco, valor, estabelecimento, parcelas e cartao corretos`() = runBlocking {
        val dao = daoWith(cardAna, cardItau)
        val result = BankNotificationProcessor(dao).process(
            "com.itau", "Itaú",
            "Compra aprovada no valor de R$ 1.850,00 em AMAZON BR em 10x de R$ 185,00 cartão final 9988.",
            postTime
        )

        assertTrue(result is AutoCaptureResult.Registered)
        result as AutoCaptureResult.Registered
        assertEquals("Itaú", result.expense.bankName)
        assertEquals(20L, result.card.id)
        assertEquals(1L, result.holder.id)

        val tx = dao.transactions.single()
        assertEquals(20L, tx.cardId)
        assertEquals(1L, tx.holderId)
        assertEquals("AMAZON BR", tx.merchant)
        assertEquals(185_000L, tx.totalAmount)
        assertEquals(10, tx.installmentsCount)
        assertEquals(postTime, tx.purchaseTimestamp)
        assertTrue(tx.isAutoCaptured)
        assertEquals(result.expense.dedupHash, tx.dedupHash)

        assertEquals(10, dao.installments.size)
        val totalCents = dao.installments.sumOf { it.amount }
        assertEquals(185_000L, totalCents)
        assertTrue(dao.installments.all { it.cardId == 20L && it.holderId == 1L && it.merchantSnapshot == "AMAZON BR" })
    }

    @Test
    fun `centavos impares sao preservados ate as parcelas gravadas`() = runBlocking {
        val dao = daoWith(cardAna)
        BankNotificationProcessor(dao).process(
            "com.nu.production", null,
            "Compra aprovada de R$ 1.000,01 em LOJA CENTRO em 7x cartão final 4321.",
            postTime
        )
        val totalCents = dao.installments.sumOf { it.amount }
        assertEquals(100_001L, totalCents)
        assertEquals(7, dao.installments.size)
    }

    @Test
    fun `pacote nao suportado nao grava nada`() = runBlocking {
        val dao = daoWith(cardAna)
        val result = BankNotificationProcessor(dao).process("com.whatsapp", "Compra aprovada", nubankText, postTime)
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.UNSUPPORTED_PACKAGE), result)
        assertTrue(dao.transactions.isEmpty())
    }

    @Test
    fun `notificacao nao reconhecida ou nula e ignorada com seguranca`() = runBlocking {
        val dao = daoWith(cardAna)
        val processor = BankNotificationProcessor(dao)
        val invoice = processor.process("com.nu.production", "Sua fatura fechou", "Sua fatura fechou no valor de R$ 2.340,10.", postTime)
        val empty = processor.process("com.nu.production", null, null, postTime)
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.NOT_A_PURCHASE_OR_AMBIGUOUS), invoice)
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.NOT_A_PURCHASE_OR_AMBIGUOUS), empty)
        assertTrue(dao.transactions.isEmpty())
        assertTrue(dao.installments.isEmpty())
    }

    @Test
    fun `final informado sem cartao cadastrado nao cai em outro cartao`() = runBlocking {
        val dao = daoWith(cardItau)
        val result = BankNotificationProcessor(dao).process("com.nu.production", "Compra aprovada", nubankText, postTime)
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.NO_MATCHING_CARD), result)
        assertTrue(dao.transactions.isEmpty())
    }

    @Test
    fun `cartao sem final usa o unico cartao cadastrado`() = runBlocking {
        val dao = daoWith(cardAna)
        val result = BankNotificationProcessor(dao).process(
            "com.nu.production", "Compra aprovada", "Compra aprovada de R$ 25,00 em CAFE DO PONTO.", postTime
        )
        assertTrue(result is AutoCaptureResult.Registered)
        assertEquals(10L, dao.transactions.single().cardId)
    }

    @Test
    fun `cartao sem final com varios cartoes e ambiguo e nao grava`() = runBlocking {
        val dao = daoWith(cardAna, cardItau)
        val result = BankNotificationProcessor(dao).process(
            "com.nu.production", "Compra aprovada", "Compra aprovada de R$ 25,00 em CAFE DO PONTO.", postTime
        )
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.AMBIGUOUS_CARD), result)
        assertTrue(dao.transactions.isEmpty())
    }

    @Test
    fun `cartao sem titular padrao nao grava`() = runBlocking {
        val dao = daoWith(cardAna.copy(defaultHolderId = null))
        val result = BankNotificationProcessor(dao).process("com.nu.production", "Compra aprovada", nubankText, postTime)
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.NO_HOLDER), result)
        assertTrue(dao.transactions.isEmpty())
    }

    @Test
    fun `notificacoes repetidas dentro de 2 minutos geram um unico lancamento`() = runBlocking {
        val dao = daoWith(cardAna)
        val processor = BankNotificationProcessor(dao)
        // Cruza a fronteira de um bucket fixo de 120s: a janela deve ser relativa, não por bucket.
        val first = processor.process("com.nu.production", "Compra aprovada", nubankText, 119_000L)
        val repeated = processor.process("com.nu.production", "Compra aprovada", nubankText, 121_000L)
        val sameInstant = processor.process("com.nu.production", "Compra aprovada", nubankText, 121_000L)

        assertTrue(first is AutoCaptureResult.Registered)
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.DUPLICATE), repeated)
        assertEquals(AutoCaptureResult.Ignored(IgnoreReason.DUPLICATE), sameInstant)
        assertEquals(1, dao.transactions.size)
    }

    @Test
    fun `mesma compra apos a janela de 2 minutos e lancada novamente`() = runBlocking {
        val dao = daoWith(cardAna)
        val processor = BankNotificationProcessor(dao)
        processor.process("com.nu.production", "Compra aprovada", nubankText, postTime)
        val later = processor.process("com.nu.production", "Compra aprovada", nubankText, postTime + 180_000L)
        assertTrue(later is AutoCaptureResult.Registered)
        assertEquals(2, dao.transactions.size)
    }

    /** DAO em memória com a mesma semântica das queries usadas no fluxo de captura. */
    private class InMemoryDao : AppDao {
        val holders = mutableListOf<HolderEntity>()
        val cards = mutableListOf<CreditCardEntity>()
        val transactions = mutableListOf<TransactionEntity>()
        val installments = mutableListOf<InstallmentEntity>()
        val payments = mutableListOf<PaymentEntity>()

        override suspend fun insertHolder(holder: HolderEntity): Long { holders += holder; return holder.id }
        override fun observeHolders(): Flow<List<HolderEntity>> = flowOf(holders.toList())
        override suspend fun getHolderById(id: Long): HolderEntity? = holders.firstOrNull { it.id == id }
        override suspend fun insertCard(card: CreditCardEntity): Long { cards += card; return card.id }
        override fun observeCards(): Flow<List<CreditCardEntity>> = flowOf(cards.toList())
        override suspend fun getAllCardsSnapshot(): List<CreditCardEntity> = cards.toList()
        override suspend fun getCardById(id: Long): CreditCardEntity? = cards.firstOrNull { it.id == id }
        override suspend fun insertTransaction(tx: TransactionEntity): Long {
            val id = transactions.size + 1L
            transactions += tx.copy(id = id)
            return id
        }
        override suspend fun insertInstallments(installments: List<InstallmentEntity>) { this.installments += installments }
        override suspend fun countRecentDuplicate(hash: String, minTimestamp: Long): Int {
            val count = transactions.count { it.dedupHash == hash && it.purchaseTimestamp >= minTimestamp }
            // Amplia a janela entre verificação e gravação: sem o lock do processador, ambas as instâncias gravariam.
            delay(50)
            return count
        }
        override fun observeInstallmentsByPeriod(month: Int, year: Int): Flow<List<InstallmentEntity>> =
            flowOf(installments.filter { it.refMonth == month && it.refYear == year })
        override fun observeAllInstallments(): Flow<List<InstallmentEntity>> = flowOf(installments.toList())
        override suspend fun insertPayment(payment: PaymentEntity): Long { payments += payment; return payment.id }
        override fun observePaymentsByPeriod(month: Int, year: Int): Flow<List<PaymentEntity>> =
            flowOf(payments.filter { it.refMonth == month && it.refYear == year })
        override fun observeAllPayments(): Flow<List<PaymentEntity>> = flowOf(payments.toList())
        override suspend fun getTotalHistoricalSpentByHolder(holderId: Long): Long =
            installments.filter { it.holderId == holderId }.sumOf { it.amount }
        override suspend fun getTotalHistoricalPaidByHolder(holderId: Long): Long =
            payments.filter { it.holderId == holderId }.sumOf { it.amountPaid }
    }
}
