package com.cardcontrol.app

import com.cardcontrol.app.data.local.AppDatabase
import com.cardcontrol.app.data.local.CreditCardEntity
import com.cardcontrol.app.data.local.HolderEntity
import com.cardcontrol.app.data.local.SupportedBank
import com.cardcontrol.app.data.repository.CardRepository
import com.cardcontrol.app.data.repository.CardResolution
import com.cardcontrol.app.service.AutoCaptureResult
import com.cardcontrol.app.service.BankNotificationProcessor
import com.cardcontrol.app.service.IgnoreReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * Validação rigorosa da migração Room v3 -> v4 e contratos de segurança do banco:
 * 1. Simulação da migração MIGRATION_3_4 preservando dados e isolando cartões ambíguos.
 * 2. Deduplicação concorrente segura sob alta disputa.
 * 3. Associação segura de notificações com cartões canônicos e bloqueio de emissores ambíguos.
 */
class RoomV3ToV4MigrationTest {

    @Test
    fun `migracao 3 para 4 classifica bancos inequivocos e mantem ambiguos como other`() {
        // Simulação do cursor de cartões v3 legados
        val legacyCards = listOf(
            1L to "Nu Roxinho",
            2L to "Meu Itaú Click",
            3L to "Inter Black",
            4L to "Cartão Pessoal",
            5L to "Cartão Nubank e Inter", // Ambíguo: 2 bancos no mesmo nome!
            6L to "Bradesco Elo",
            7L to "C6 Carbon"
        )

        val updates = mutableMapOf<Long, String>()
        for ((id, name) in legacyCards) {
            val inferred = SupportedBank.inferFromCardName(name)
            if (inferred != SupportedBank.OTHER) {
                updates[id] = inferred.id
            }
        }

        // Verifica que bancos inequívocos foram atualizados
        assertEquals("nubank", updates[1L])
        assertEquals("itau", updates[2L])
        assertEquals("inter", updates[3L])
        assertEquals("bradesco", updates[6L])
        assertEquals("c6", updates[7L])

        // Garante que cartão sem banco e cartão ambíguo NÃO foram atualizados (permanecem 'other')
        assertTrue("Cartão genérico não pode ser associado", !updates.containsKey(4L))
        assertTrue("Cartão com múltiplos bancos no nome deve permanecer desconhecido (ambíguo)", !updates.containsKey(5L))
    }

    @Test
    fun `inferFromCardName isola casos perigosos de colisao de texto`() {
        // Palavras que contém substrings como 'inter' mas não são banco Inter
        assertEquals(SupportedBank.OTHER, SupportedBank.inferFromCardName("Interessante"))
        assertEquals(SupportedBank.OTHER, SupportedBank.inferFromCardName("Internacional"))
        assertEquals(SupportedBank.OTHER, SupportedBank.inferFromCardName("Cartão Geral"))
        assertEquals(SupportedBank.OTHER, SupportedBank.inferFromCardName("Meu Cartao"))
        assertEquals(SupportedBank.OTHER, SupportedBank.inferFromCardName("Nubank e Itaú juntos"))

        // Casos inequívocos suportados
        assertEquals(SupportedBank.NUBANK, SupportedBank.inferFromCardName("Nubank Principal"))
        assertEquals(SupportedBank.NUBANK, SupportedBank.inferFromCardName("Nu Ana"))
        assertEquals(SupportedBank.ITAU, SupportedBank.inferFromCardName("Meu Itaú"))
        assertEquals(SupportedBank.ITAU, SupportedBank.inferFromCardName("Itaucard Platinum"))
        assertEquals(SupportedBank.INTER, SupportedBank.inferFromCardName("Banco Inter"))
        assertEquals(SupportedBank.C6, SupportedBank.inferFromCardName("C6 Bank"))
        assertEquals(SupportedBank.SANTANDER, SupportedBank.inferFromCardName("Santander SX"))
        assertEquals(SupportedBank.BRADESCO, SupportedBank.inferFromCardName("Bradesco Prime"))
        assertEquals(SupportedBank.CAIXA, SupportedBank.inferFromCardName("Caixa Tem"))
        assertEquals(SupportedBank.PICPAY, SupportedBank.inferFromCardName("PicPay Card"))
        assertEquals(SupportedBank.MERCADO_PAGO, SupportedBank.inferFromCardName("Mercado Pago"))
    }

    @Test
    fun `resolucao de cartao bloqueia cartoes ambiguos ou de outros emissores`() = runBlocking {
        val holder = HolderEntity(id = 1, name = "Titular Teste", whatsapp = "11999999999")
        val cardNu = CreditCardEntity(id = 10, name = "Nu Roxinho", bank = SupportedBank.NUBANK.id, lastFourDigits = "1234", totalLimit = 500_000L, closingDay = 5, dueDay = 12, defaultHolderId = 1)
        val cardAmbiguo = CreditCardEntity(id = 20, name = "Nu e Itaú", bank = SupportedBank.OTHER.id, lastFourDigits = "1234", totalLimit = 500_000L, closingDay = 5, dueDay = 12, defaultHolderId = 1)
        val cardOutro = CreditCardEntity(id = 30, name = "Cartão Geral", bank = SupportedBank.OTHER.id, lastFourDigits = "1234", totalLimit = 500_000L, closingDay = 5, dueDay = 12, defaultHolderId = 1)

        val testDao = TestDao()
        testDao.insertHolder(holder)
        testDao.insertCard(cardNu)
        testDao.insertCard(cardAmbiguo)
        testDao.insertCard(cardOutro)

        val repo = CardRepository(testDao)

        // Com mais de um cartão com final 1234 onde um é Nubank e os outros são 'other', mas um 'other' tem 'nu' no nome:
        // 'Nu e Itaú' é ambíguo e inferFromCardName resulta em OTHER.
        // Logo, para o pacote com.nu.production, o único cartão confirmado é cardNu!
        val resolved = repo.resolveCard("com.nu.production", "1234")
        assertTrue("Esperava resolução única para o cartão Nubank inequívoco", resolved is CardResolution.Found)
        assertEquals(10L, (resolved as CardResolution.Found).card.id)

        // Notificação do Itaú com final 1234: nenhum cartão é Itaú (cardAmbiguo não pode ser associado)
        val resolvedItau = repo.resolveCard("com.itau", "1234")
        assertTrue("Cartão ambíguo não pode receber compra do Itaú", resolvedItau is CardResolution.Ambiguous)
    }

    @Test
    fun `processador integrado com lock serializado previne duplicatas concorrentes`() = runBlocking {
        val holder = HolderEntity(id = 1, name = "Titular Concorrencia", whatsapp = "11999999999")
        val card = CreditCardEntity(id = 10, name = "Nu Teste", bank = SupportedBank.NUBANK.id, lastFourDigits = "8899", totalLimit = 1_000_000L, closingDay = 10, dueDay = 17, defaultHolderId = 1)

        val testDao = TestDao()
        testDao.insertHolder(holder)
        testDao.insertCard(card)

        val processor1 = BankNotificationProcessor(testDao)
        val processor2 = BankNotificationProcessor(testDao)
        val processor3 = BankNotificationProcessor(testDao)

        val text = "Compra de R$ 150,00 APROVADA em SUPERMERCADO DIA para o cartão com final 8899."
        val postTime = 1_760_000_100_000L

        // Dispara 3 requisições em paralelo com a mesma notificação
        val results = listOf(
            async(Dispatchers.Default) { processor1.process("com.nu.production", "Compra aprovada", text, postTime) },
            async(Dispatchers.Default) { processor2.process("com.nu.production", "Compra aprovada", text, postTime) },
            async(Dispatchers.Default) { processor3.process("com.nu.production", "Compra aprovada", text, postTime) }
        ).awaitAll()

        // Exatamente um foi registrado, e os outros dois foram ignorados por duplicidade
        val registeredCount = results.count { it is AutoCaptureResult.Registered }
        val duplicateCount = results.count { it == AutoCaptureResult.Ignored(IgnoreReason.DUPLICATE) }

        assertEquals(1, registeredCount)
        assertEquals(2, duplicateCount)
        assertEquals(1, testDao.transactions.size)
        assertEquals(15_000L, testDao.transactions.first().totalAmount)
    }

    private class TestDao : com.cardcontrol.app.data.local.AppDao {
        val holders = Collections.synchronizedList(mutableListOf<HolderEntity>())
        val cards = Collections.synchronizedList(mutableListOf<CreditCardEntity>())
        val transactions = Collections.synchronizedList(mutableListOf<com.cardcontrol.app.data.local.TransactionEntity>())
        val installments = Collections.synchronizedList(mutableListOf<com.cardcontrol.app.data.local.InstallmentEntity>())
        val payments = Collections.synchronizedList(mutableListOf<com.cardcontrol.app.data.local.PaymentEntity>())

        override suspend fun insertHolder(holder: HolderEntity): Long {
            holders.add(holder)
            return holder.id
        }
        override fun observeHolders(): kotlinx.coroutines.flow.Flow<List<HolderEntity>> =
            kotlinx.coroutines.flow.flowOf(holders.toList())
        override suspend fun getHolderById(id: Long): HolderEntity? = holders.firstOrNull { it.id == id }

        override suspend fun insertCard(card: CreditCardEntity): Long {
            cards.add(card)
            return card.id
        }
        override fun observeCards(): kotlinx.coroutines.flow.Flow<List<CreditCardEntity>> =
            kotlinx.coroutines.flow.flowOf(cards.toList())
        override suspend fun getAllCardsSnapshot(): List<CreditCardEntity> = cards.toList()
        override suspend fun getCardById(id: Long): CreditCardEntity? = cards.firstOrNull { it.id == id }

        override suspend fun insertTransaction(tx: com.cardcontrol.app.data.local.TransactionEntity): Long {
            val id = transactions.size + 1L
            transactions.add(tx.copy(id = id))
            return id
        }
        override suspend fun insertInstallments(installments: List<com.cardcontrol.app.data.local.InstallmentEntity>) {
            this.installments.addAll(installments)
        }
        override suspend fun countRecentDuplicate(hash: String, minTimestamp: Long): Int {
            return transactions.count { it.dedupHash == hash && it.purchaseTimestamp >= minTimestamp }
        }
        override fun observeInstallmentsByPeriod(month: Int, year: Int): kotlinx.coroutines.flow.Flow<List<com.cardcontrol.app.data.local.InstallmentEntity>> =
            kotlinx.coroutines.flow.flowOf(installments.filter { it.refMonth == month && it.refYear == year })
        override fun observeAllInstallments(): kotlinx.coroutines.flow.Flow<List<com.cardcontrol.app.data.local.InstallmentEntity>> =
            kotlinx.coroutines.flow.flowOf(installments.toList())

        override suspend fun insertPayment(payment: com.cardcontrol.app.data.local.PaymentEntity): Long {
            payments.add(payment)
            return payment.id
        }
        override fun observePaymentsByPeriod(month: Int, year: Int): kotlinx.coroutines.flow.Flow<List<com.cardcontrol.app.data.local.PaymentEntity>> =
            kotlinx.coroutines.flow.flowOf(payments.filter { it.refMonth == month && it.refYear == year })
        override fun observeAllPayments(): kotlinx.coroutines.flow.Flow<List<com.cardcontrol.app.data.local.PaymentEntity>> =
            kotlinx.coroutines.flow.flowOf(payments.toList())

        override suspend fun getTotalHistoricalSpentByHolder(holderId: Long): Long =
            installments.filter { it.holderId == holderId }.sumOf { it.amount }
        override suspend fun getTotalHistoricalPaidByHolder(holderId: Long): Long =
            payments.filter { it.holderId == holderId }.sumOf { it.amountPaid }
    }
}
