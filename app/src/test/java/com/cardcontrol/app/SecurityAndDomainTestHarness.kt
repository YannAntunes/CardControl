package com.cardcontrol.app

import com.cardcontrol.app.data.local.AppDao
import com.cardcontrol.app.data.local.CreditCardEntity
import com.cardcontrol.app.data.local.HolderEntity
import com.cardcontrol.app.data.local.InstallmentEntity
import com.cardcontrol.app.data.local.PaymentEntity
import com.cardcontrol.app.data.local.TransactionEntity
import com.cardcontrol.app.data.repository.CardRepository
import com.cardcontrol.app.security.SecuritySanitizer
import com.cardcontrol.app.service.BankNotificationParser
import com.cardcontrol.app.util.MoneyUtils
import com.cardcontrol.app.util.ReportEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Collections

private class FakeAppDao : AppDao {
    val holders = Collections.synchronizedList(mutableListOf<HolderEntity>())
    val cards = Collections.synchronizedList(mutableListOf<CreditCardEntity>())
    val transactions = Collections.synchronizedList(mutableListOf<TransactionEntity>())
    val installments = Collections.synchronizedList(mutableListOf<InstallmentEntity>())
    val payments = Collections.synchronizedList(mutableListOf<PaymentEntity>())

    override suspend fun insertHolder(holder: HolderEntity): Long = synchronized(holders) {
        holders.add(holder)
        holders.size.toLong()
    }
    override fun observeHolders(): Flow<List<HolderEntity>> = synchronized(holders) { flowOf(holders.toList()) }
    override suspend fun getHolderById(id: Long): HolderEntity? = synchronized(holders) { holders.find { it.id == id } }

    override suspend fun insertCard(card: CreditCardEntity): Long = synchronized(cards) {
        cards.add(card)
        cards.size.toLong()
    }
    override fun observeCards(): Flow<List<CreditCardEntity>> = synchronized(cards) { flowOf(cards.toList()) }
    override suspend fun getAllCardsSnapshot(): List<CreditCardEntity> = synchronized(cards) { cards.toList() }
    override suspend fun getCardById(id: Long): CreditCardEntity? = synchronized(cards) { cards.find { it.id == id } }

    override suspend fun insertTransaction(tx: TransactionEntity): Long = synchronized(transactions) {
        transactions.add(tx)
        transactions.size.toLong()
    }
    override suspend fun insertInstallments(installments: List<InstallmentEntity>) {
        this.installments.addAll(installments)
    }
    override suspend fun countRecentDuplicate(hash: String, minTimestamp: Long): Int = synchronized(transactions) {
        transactions.count { it.dedupHash == hash && it.purchaseTimestamp >= minTimestamp }
    }
    override fun observeInstallmentsByPeriod(month: Int, year: Int): Flow<List<InstallmentEntity>> = synchronized(installments) { flowOf(installments.toList()) }
    override fun observeAllInstallments(): Flow<List<InstallmentEntity>> = synchronized(installments) { flowOf(installments.toList()) }

    override suspend fun insertPayment(payment: PaymentEntity): Long = synchronized(payments) {
        payments.add(payment)
        payments.size.toLong()
    }
    override fun observePaymentsByPeriod(month: Int, year: Int): Flow<List<PaymentEntity>> = synchronized(payments) { flowOf(payments.toList()) }
    override fun observeAllPayments(): Flow<List<PaymentEntity>> = synchronized(payments) { flowOf(payments.toList()) }

    override suspend fun getTotalHistoricalSpentByHolder(holderId: Long): Long = synchronized(installments) {
        installments.filter { it.holderId == holderId }.sumOf { it.amount }
    }

    override suspend fun getTotalHistoricalPaidByHolder(holderId: Long): Long = synchronized(payments) {
        payments.filter { it.holderId == holderId }.sumOf { it.amountPaid }
    }

    val recurringCharges = Collections.synchronizedList(mutableListOf<com.cardcontrol.app.data.local.RecurringChargeEntity>())

    override suspend fun insertRecurringCharge(charge: com.cardcontrol.app.data.local.RecurringChargeEntity): Long = synchronized(recurringCharges) {
        recurringCharges.removeAll { it.merchant.equals(charge.merchant, ignoreCase = true) && it.cardId == charge.cardId }
        val newId = if (charge.id == 0L) (recurringCharges.size + 1).toLong() else charge.id
        val toAdd = charge.copy(id = newId)
        recurringCharges.add(toAdd)
        newId
    }

    override fun observeActiveRecurringCharges(): Flow<List<com.cardcontrol.app.data.local.RecurringChargeEntity>> = synchronized(recurringCharges) {
        flowOf(recurringCharges.filter { it.isActive })
    }

    override fun observeAllRecurringCharges(): Flow<List<com.cardcontrol.app.data.local.RecurringChargeEntity>> = synchronized(recurringCharges) {
        flowOf(recurringCharges.toList())
    }

    override suspend fun getAllRecurringChargesSnapshot(): List<com.cardcontrol.app.data.local.RecurringChargeEntity> = synchronized(recurringCharges) {
        recurringCharges.toList()
    }

    override suspend fun getRecurringChargeByMerchantAndCard(merchant: String, cardId: Long): com.cardcontrol.app.data.local.RecurringChargeEntity? = synchronized(recurringCharges) {
        recurringCharges.firstOrNull { it.merchant.equals(merchant, ignoreCase = true) && it.cardId == cardId }
    }

    override suspend fun setRecurringChargeActive(id: Long, isActive: Boolean) {
        synchronized(recurringCharges) {
            val idx = recurringCharges.indexOfFirst { it.id == id }
            if (idx != -1) {
                recurringCharges[idx] = recurringCharges[idx].copy(isActive = isActive)
            }
        }
    }

    override suspend fun setTransactionRecurring(transactionId: Long, isRecurring: Boolean) {
        synchronized(transactions) {
            val idx = transactions.indexOfFirst { it.id == transactionId }
            if (idx != -1) {
                transactions[idx] = transactions[idx].copy(isRecurring = isRecurring)
            }
        }
    }

    override suspend fun getTransactionsByMerchant(merchant: String): List<TransactionEntity> = synchronized(transactions) {
        transactions.filter { it.merchant.equals(merchant, ignoreCase = true) }.sortedBy { it.purchaseTimestamp }
    }

    override suspend fun getAllTransactionsSnapshot(): List<TransactionEntity> = synchronized(transactions) {
        transactions.toList().sortedBy { it.purchaseTimestamp }
    }

    override suspend fun getTotalActiveRecurringAmount(): Long = synchronized(recurringCharges) {
        recurringCharges.filter { it.isActive }.sumOf { it.expectedAmount }
    }
}

class SecurityAndDomainTestHarness {

    @Test
    fun `harness deve bloquear tentativa de Path Traversal no nome do titular`() {
        val maliciousName = "../../etc/passwd\u0000; rm -rf"
        val sanitized = SecuritySanitizer.sanitizeFileNameSegment(maliciousName)
        assertFalse(sanitized.contains(".."))
        assertFalse(sanitized.contains("/"))
        assertEquals("etcpasswd_rm_-rf", sanitized)
    }

    @Test
    fun `harness deve neutralizar injecao de cabecalho SMTP CRLF`() {
        val maliciousMerchant = "LOJA X\r\nBcc: atacante@dominio.com\r\nSubject: Hacked"
        val sanitized = SecuritySanitizer.sanitizeHeaderField(maliciousMerchant)
        assertFalse(sanitized.contains("\r"))
        assertFalse(sanitized.contains("\n"))
        assertTrue(sanitized.startsWith("LOJA X Bcc:"))
    }

    @Test
    fun `harness deve ignorar notificacoes de compra negada ou estorno`() {
        val denied = BankNotificationParser.parse(
            packageName = "com.nu.production",
            title = "Compra negada",
            text = "Compra aprovada anteriormente foi estornada no valor de R$ 350,00 no cartao final 4321"
        )
        assertNull(denied)
    }

    @Test
    fun `harness deve rejeitar pacotes nao bancarios mesmo com texto identico`() {
        val spoofed = BankNotificationParser.parse(
            packageName = "com.whatsapp",
            title = "Compra aprovada",
            text = "Compra aprovada de R$ 500,00 em MERCADO LIVRE no cartao final 4321"
        )
        assertNull(spoofed)
    }

    @Test
    fun `harness deve truncar payload gigante evitando ReDoS`() {
        val giantNoise = "A".repeat(50_000)
        val bounded = SecuritySanitizer.boundNotificationInput(giantNoise)
        assertEquals(1000, bounded.length)
    }

    @Test
    fun `harness deve manter soma exata de centavos em parcelamentos impares`() {
        val parcels = CardRepository.splitInstallmentAmounts(100000L, 7)
        assertEquals(7, parcels.size)
        assertEquals(14286L, parcels.first())
        assertEquals(14285L, parcels.last())
        assertEquals(100000L, parcels.sum())
    }

    @Test
    fun `harness deve converter strings monetarias para centavos exatos via MoneyUtils`() {
        assertEquals(100000L, MoneyUtils.parseToCents("R$ 1.000,00"))
        assertEquals(35050L, MoneyUtils.parseToCents("350,50"))
        assertEquals(1000L, MoneyUtils.parseToCents("10"))
        assertEquals(1000L, MoneyUtils.parseToCents("10.00"))
    }

    @Test
    fun `harness deve avancar fatura na virada de ano quando compra ocorre apos fechamento de dezembro`() {
        val decPurchaseAfterClosing = LocalDate.of(2026, 12, 15)
        val firstParcelDate = CardRepository.calculateFirstInstallmentMonth(decPurchaseAfterClosing, closingDay = 10)
        assertEquals(2027, firstParcelDate.year)
        assertEquals(1, firstParcelDate.monthValue)
    }

    @Test
    fun `harness deve gerar dedupHash consistente e imutavel independente do timestamp`() {
        val expense1 = BankNotificationParser.parse(
            packageName = "com.nu.production",
            title = "Compra aprovada",
            text = "Compra aprovada no valor de R$ 150,00 no estabelecimento AMAZON com cartao final 1234",
            timestamp = 1700000000000L
        )
        val expense2 = BankNotificationParser.parse(
            packageName = "com.nu.production",
            title = "Compra aprovada",
            text = "Compra aprovada no valor de R$ 150,00 no estabelecimento AMAZON com cartao final 1234",
            timestamp = 1700000500000L
        )
        assertNotNull(expense1)
        assertNotNull(expense2)
        assertEquals(expense1!!.dedupHash, expense2!!.dedupHash)
    }

    @Test
    fun `harness deve permitir cadastrar titular com sublimite zero sem lancar excecao`() = runBlocking {
        val fakeDao = FakeAppDao()
        val repo = CardRepository(fakeDao)

        repo.addHolder(name = "Maria Zero", whatsapp = "11988887777", email = "maria@teste.com", subLimit = 0L)
        assertEquals(1, fakeDao.holders.size)
        assertEquals(0L, fakeDao.holders.first().subLimit)
        assertEquals("Maria Zero", fakeDao.holders.first().name)
    }

    @Test
    fun `harness deve resolver cartao unico quando notificacao nao possui ultimos quatro digitos`() = runBlocking {
        val fakeDao = FakeAppDao()
        val repo = CardRepository(fakeDao)

        fakeDao.cards.add(
            CreditCardEntity(
                id = 10L,
                name = "Meu Nubank",
                lastFourDigits = "1234",
                totalLimit = 500000L,
                closingDay = 5,
                dueDay = 15,
                defaultHolderId = 1L
            )
        )

        val resolved = repo.resolveCardForNotification(packageName = "com.nu.production", lastFourDigits = null)
        assertNotNull(resolved)
        assertEquals(10L, resolved?.id)
    }

    @Test
    fun `harness deve retornar null por ambiguidade quando usuario possui multiplos cartoes e notificacao nao tem 4 digitos`() = runBlocking {
        val fakeDao = FakeAppDao()
        val repo = CardRepository(fakeDao)

        fakeDao.cards.add(
            CreditCardEntity(
                id = 10L,
                name = "Meu Nubank",
                lastFourDigits = "1234",
                totalLimit = 500000L,
                closingDay = 5,
                dueDay = 15,
                defaultHolderId = 1L
            )
        )
        fakeDao.cards.add(
            CreditCardEntity(
                id = 20L,
                name = "Meu Itau",
                lastFourDigits = "5678",
                totalLimit = 300000L,
                closingDay = 10,
                dueDay = 20,
                defaultHolderId = 1L
            )
        )

        val resolved = repo.resolveCardForNotification(packageName = "com.nu.production", lastFourDigits = null)
        assertNull(resolved)
    }

    @Test
    fun `harness deve desempatar cartoes com mesmo final de 4 digitos pelo pacote do banco`() = runBlocking {
        val fakeDao = FakeAppDao()
        val repo = CardRepository(fakeDao)

        val cardNu = CreditCardEntity(
            id = 1L,
            name = "Nubank Ultravioleta",
            lastFourDigits = "9999",
            totalLimit = 1000000L,
            closingDay = 5,
            dueDay = 15,
            defaultHolderId = 1L
        )
        val cardItau = CreditCardEntity(
            id = 2L,
            name = "Itaú Personnalité",
            lastFourDigits = "9999",
            totalLimit = 1000000L,
            closingDay = 5,
            dueDay = 15,
            defaultHolderId = 1L
        )
        fakeDao.cards.addAll(listOf(cardNu, cardItau))

        val resolvedNu = repo.resolveCardForNotification(packageName = "com.nu.production", lastFourDigits = "9999")
        assertNotNull(resolvedNu)
        assertEquals(1L, resolvedNu?.id)

        val resolvedItau = repo.resolveCardForNotification(packageName = "com.itau", lastFourDigits = "9999")
        assertNotNull(resolvedItau)
        assertEquals(2L, resolvedItau?.id)

        val resolvedUnknown = repo.resolveCardForNotification(packageName = "com.desconhecido.app", lastFourDigits = "9999")
        assertNull(resolvedUnknown)
    }

    @Test
    fun `harness deve paginar lancamentos exatamente nos limites de 0, 22, 23 e 51 itens`() {
        val dummy = InstallmentEntity(
            transactionId = 1L,
            holderId = 1L,
            cardId = 1L,
            merchantSnapshot = "Item",
            installmentNumber = 1,
            totalInstallments = 1,
            amount = 1000L,
            refMonth = 10,
            refYear = 2026,
            purchaseTimestamp = 1700000000000L
        )

        // 0 itens: 1 página vazia
        val p0 = ReportEngine.paginateInstallments(emptyList())
        assertEquals(1, p0.size)
        assertTrue(p0[0].isEmpty())

        // 22 itens: exatamente 1 página cheia
        val p22 = ReportEngine.paginateInstallments(List(22) { dummy })
        assertEquals(1, p22.size)
        assertEquals(22, p22[0].size)

        // 23 itens: quebra para 2 páginas (22 na 1ª, 1 na 2ª)
        val p23 = ReportEngine.paginateInstallments(List(23) { dummy })
        assertEquals(2, p23.size)
        assertEquals(22, p23[0].size)
        assertEquals(1, p23[1].size)

        // 51 itens: quebra para 3 páginas (22 na 1ª, 28 na 2ª, 1 na 3ª)
        val p51 = ReportEngine.paginateInstallments(List(51) { dummy })
        assertEquals(3, p51.size)
        assertEquals(22, p51[0].size)
        assertEquals(28, p51[1].size)
        assertEquals(1, p51[2].size)
    }

    @Test
    fun `harness deve processar notificacao ponta a ponta para compras a vista e parceladas com milhar`() = runBlocking {
        val fakeDao = FakeAppDao()
        val repo = CardRepository(fakeDao)

        val holderId = fakeDao.insertHolder(
            HolderEntity(id = 1L, name = "Kauan", whatsapp = "11999999999", email = "k@k.com", subLimit = 1000000L)
        )
        fakeDao.insertCard(
            CreditCardEntity(id = 1L, name = "Nubank", lastFourDigits = "4321", totalLimit = 500000L, closingDay = 5, dueDay = 15, defaultHolderId = holderId)
        )
        fakeDao.insertCard(
            CreditCardEntity(id = 2L, name = "Itaú", lastFourDigits = "9988", totalLimit = 1000000L, closingDay = 10, dueDay = 20, defaultHolderId = holderId)
        )

        // Compra à vista: R$ 45,90 em PADARIA CENTRAL no Nubank final 4321
        val (tx1, card1) = repo.processBankNotification(
            packageName = "com.nu.production",
            title = "Compra no crédito aprovada",
            text = "Compra aprovada de R$ 45,90 em PADARIA CENTRAL cartão final 4321.",
            timestamp = 1700000000000L
        )
        assertTrue(tx1 > 0L)
        assertEquals(1L, card1?.id)
        assertEquals(1, fakeDao.transactions.size)
        assertEquals(4590L, fakeDao.transactions.first().totalAmount)
        assertEquals("PADARIA CENTRAL", fakeDao.transactions.first().merchant)
        assertEquals(1, fakeDao.transactions.first().installmentsCount)
        assertEquals(1, fakeDao.installments.size)
        assertEquals(4590L, fakeDao.installments.first().amount)

        // Compra parcelada com milhar: R$ 1.850,00 em AMAZON BR em 10x no Itaú final 9988
        val (tx2, card2) = repo.processBankNotification(
            packageName = "com.itau",
            title = "Itaú Unibanco",
            text = "Compra aprovada no valor de R$ 1.850,00 em AMAZON BR em 10x cartão final 9988.",
            timestamp = 1700000010000L
        )
        assertTrue(tx2 > 0L)
        assertEquals(2L, card2?.id)
        assertEquals(2, fakeDao.transactions.size)
        val secondTx = fakeDao.transactions[1]
        assertEquals(185000L, secondTx.totalAmount)
        assertEquals("AMAZON BR", secondTx.merchant)
        assertEquals(10, secondTx.installmentsCount)

        val amazonInstallments = fakeDao.installments.filter { it.transactionId == tx2 }
        assertEquals(10, amazonInstallments.size)
        assertEquals(185000L, amazonInstallments.sumOf { it.amount })
    }

    @Test
    fun `harness deve ignorar pacotes invalidos, fatura fechada, oferta, compra negada e estorno sem persistir despesas`() = runBlocking {
        val fakeDao = FakeAppDao()
        val repo = CardRepository(fakeDao)

        val holderId = fakeDao.insertHolder(
            HolderEntity(id = 1L, name = "Kauan", whatsapp = "11999999999", email = "k@k.com", subLimit = 1000000L)
        )
        fakeDao.insertCard(
            CreditCardEntity(id = 1L, name = "Nubank", lastFourDigits = "4321", totalLimit = 500000L, closingDay = 5, dueDay = 15, defaultHolderId = holderId)
        )

        // 1. Pacote não suportado (ex: WhatsApp spoofing)
        val res1 = repo.processBankNotification("com.whatsapp", "Compra aprovada", "R$ 100,00 em LOJA cartão final 4321", 1700000000000L)
        assertEquals(-1L, res1.first)

        // 2. Fatura fechada
        val res2 = repo.processBankNotification("com.nu.production", "Sua fatura fechou", "Sua fatura fechou no valor de R$ 2.340,10 e vence dia 10", 1700000000000L)
        assertEquals(-1L, res2.first)

        // 3. Oferta de limite
        val res3 = repo.processBankNotification("br.com.intermedium", "Oferta especial!", "Você ganhou aumento de limite de R$ 500,00 no seu cartão", 1700000000000L)
        assertEquals(-1L, res3.first)

        // 4. Compra negada
        val res4 = repo.processBankNotification("com.nu.production", "Compra negada", "Tentativa de compra negada de R$ 350,00 no cartão final 4321", 1700000000000L)
        assertEquals(-1L, res4.first)

        // 5. Estorno
        val res5 = repo.processBankNotification("com.nu.production", "Estorno confirmado", "Compra de R$ 200,00 foi estornada no cartão final 4321", 1700000000000L)
        assertEquals(-1L, res5.first)

        assertEquals(0, fakeDao.transactions.size)
        assertEquals(0, fakeDao.installments.size)
    }

    @Test
    fun `harness deve criar despesa sem 4 digitos para cartao unico e rejeitar por ambiguidade se houver dois cartoes`() = runBlocking {
        val fakeDao = FakeAppDao()
        val repo = CardRepository(fakeDao)

        val holderId = fakeDao.insertHolder(
            HolderEntity(id = 1L, name = "Kauan", whatsapp = "11999999999", email = "k@k.com", subLimit = 1000000L)
        )
        fakeDao.insertCard(
            CreditCardEntity(id = 10L, name = "Nubank", lastFourDigits = "1111", totalLimit = 500000L, closingDay = 5, dueDay = 15, defaultHolderId = holderId)
        )

        // Notificação sem 4 dígitos com 1 único cartão cadastrado -> Cria despesa com sucesso
        val (tx1, card1) = repo.processBankNotification(
            packageName = "com.nu.production",
            title = "Compra aprovada",
            text = "Compra aprovada no valor de R$ 90,00 no SUPERMERCADO",
            timestamp = 1700000000000L
        )
        assertTrue(tx1 > 0L)
        assertEquals(10L, card1?.id)
        assertEquals(1, fakeDao.transactions.size)

        // Adiciona um 2º cartão -> Notificação sem 4 dígitos fica ambígua
        fakeDao.insertCard(
            CreditCardEntity(id = 20L, name = "Inter", lastFourDigits = "2222", totalLimit = 500000L, closingDay = 10, dueDay = 20, defaultHolderId = holderId)
        )

        val (tx2, card2) = repo.processBankNotification(
            packageName = "com.nu.production",
            title = "Compra aprovada",
            text = "Compra aprovada no valor de R$ 120,00 no POSTO GASOLINA",
            timestamp = 1700000020000L
        )
        assertEquals(-1L, tx2)
        assertNull(card2)
        assertEquals(1, fakeDao.transactions.size)
    }

    @Test
    fun `harness deve descartar notificacao repetida dentro da janela de 120 segundos`() = runBlocking {
        val fakeDao = FakeAppDao()
        val repo = CardRepository(fakeDao)

        val holderId = fakeDao.insertHolder(
            HolderEntity(id = 1L, name = "Kauan", whatsapp = "11999999999", email = "k@k.com", subLimit = 1000000L)
        )
        fakeDao.insertCard(
            CreditCardEntity(id = 1L, name = "Nubank", lastFourDigits = "4321", totalLimit = 500000L, closingDay = 5, dueDay = 15, defaultHolderId = holderId)
        )

        val t0 = 1700000000000L
        // 1ª notificação legítima
        val (tx1, card1) = repo.processBankNotification(
            packageName = "com.nu.production",
            title = "Compra no crédito aprovada",
            text = "Compra aprovada de R$ 50,00 em FARMACIA no cartão final 4321",
            timestamp = t0
        )
        assertTrue(tx1 > 0L)
        assertNotNull(card1)
        assertEquals(1, fakeDao.transactions.size)

        // 2ª notificação duplicada recebida 45 segundos depois (dentro dos 120s)
        val (tx2, card2) = repo.processBankNotification(
            packageName = "com.nu.production",
            title = "Compra no crédito aprovada",
            text = "Compra aprovada de R$ 50,00 em FARMACIA no cartão final 4321",
            timestamp = t0 + 45_000L
        )
        assertEquals(-1L, tx2)
        assertNull(card2)
        assertEquals(1, fakeDao.transactions.size)
    }

    @Test
    fun `harness deve garantir deduplicacao atomica sob concorrencia com duas instancias de repositorio`() = runBlocking {
        val fakeDao = FakeAppDao()
        val repo1 = CardRepository(fakeDao)
        val repo2 = CardRepository(fakeDao)

        val holderId = fakeDao.insertHolder(
            HolderEntity(id = 1L, name = "Kauan", whatsapp = "11999999999", email = "k@k.com", subLimit = 1000000L)
        )
        fakeDao.insertCard(
            CreditCardEntity(id = 1L, name = "Nubank", lastFourDigits = "4321", totalLimit = 500000L, closingDay = 5, dueDay = 15, defaultHolderId = holderId)
        )

        val t0 = 1700000000000L
        val pkg = "com.nu.production"
        val title = "Compra no crédito aprovada"
        val text = "Compra aprovada de R$ 150,00 em RESTAURANTE no cartão final 4321"

        // Dispara simultaneamente através de duas instâncias independentes de CardRepository
        val deferred1 = async(Dispatchers.Default) {
            repo1.processBankNotification(pkg, title, text, t0)
        }
        val deferred2 = async(Dispatchers.Default) {
            repo2.processBankNotification(pkg, title, text, t0)
        }

        val results = awaitAll(deferred1, deferred2)
        val successful = results.filter { it.first > 0L }
        val deduplicated = results.filter { it.first == -1L }

        // Exatamente uma instância obtém sucesso e a outra é deduplicada pelo mutex estático e hash persistido
        assertEquals(1, successful.size)
        assertEquals(1, deduplicated.size)
        assertEquals(1, fakeDao.transactions.size)
        assertEquals(1, fakeDao.installments.size)
    }

    @Test
    fun `harness deve aceitar compras legitimas mesmo que o estabelecimento ou texto contenha palavras genericas como oferta, token ou mencao a cashback`() = runBlocking {
        val fakeDao = FakeAppDao()
        val repo = CardRepository(fakeDao)

        val holderId = fakeDao.insertHolder(
            HolderEntity(id = 1L, name = "Kauan", whatsapp = "11999999999", email = "k@k.com", subLimit = 1000000L)
        )
        fakeDao.insertCard(
            CreditCardEntity(id = 1L, name = "Nubank", lastFourDigits = "4321", totalLimit = 500000L, closingDay = 5, dueDay = 15, defaultHolderId = holderId)
        )

        // 1. Estabelecimento contendo 'OFERTA'
        val (tx1, card1) = repo.processBankNotification(
            packageName = "com.nu.production",
            title = "Compra aprovada",
            text = "Compra aprovada de R$ 89,90 em SUPERMERCADO DA OFERTA cartão final 4321",
            timestamp = 1700000000000L
        )
        assertTrue(tx1 > 0L)
        assertNotNull(card1)
        assertEquals("SUPERMERCADO DA OFERTA", fakeDao.transactions.last().merchant)

        // 2. Estabelecimento contendo 'TOKEN'
        val (tx2, card2) = repo.processBankNotification(
            packageName = "com.nu.production",
            title = "Compra aprovada",
            text = "Compra aprovada de R$ 120,00 em TOKEN INFORMATICA cartão final 4321",
            timestamp = 1700000030000L
        )
        assertTrue(tx2 > 0L)
        assertNotNull(card2)
        assertEquals("TOKEN INFORMATICA", fakeDao.transactions.last().merchant)

        // 3. Notificação legítima com menção a cashback na mensagem do banco
        val (tx3, card3) = repo.processBankNotification(
            packageName = "com.nu.production",
            title = "Compra no cartão aprovada",
            text = "Compra aprovada de R$ 55,00 em DROGARIA CENTRAL final 4321. Voce acumulou cashback nesta compra!",
            timestamp = 1700000060000L
        )
        assertTrue(tx3 > 0L)
        assertNotNull(card3)
        assertEquals("DROGARIA CENTRAL", fakeDao.transactions.last().merchant)
    }

    @Test
    fun `harness deve sincronizar e persistir cobrancas recorrentes atraves do repositorio`() = runBlocking {
        val fakeDao = FakeAppDao()
        val repo = CardRepository(fakeDao)

        val cardId = fakeDao.insertCard(CreditCardEntity(id = 1, name = "Black", lastFourDigits = "9999", totalLimit = 1000000L, closingDay = 5, dueDay = 15, defaultHolderId = 1))
        val holderId = fakeDao.insertHolder(HolderEntity(id = 1, name = "Carlos", whatsapp = "11999999999", email = "c@test.com", subLimit = 500000L))

        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - java.util.concurrent.TimeUnit.DAYS.toMillis(30)

        fakeDao.insertTransaction(TransactionEntity(id = 101, cardId = cardId, holderId = holderId, merchant = "CHATGPT PLUS", totalAmount = 10000L, purchaseTimestamp = thirtyDaysAgo))
        fakeDao.insertTransaction(TransactionEntity(id = 102, cardId = cardId, holderId = holderId, merchant = "CHATGPT PLUS", totalAmount = 11000L, purchaseTimestamp = now))

        repo.syncRecurringCharges()

        val recurring = fakeDao.getAllRecurringChargesSnapshot()
        assertEquals(1, recurring.size)
        val chatGpt = recurring.first()
        assertEquals("CHATGPT PLUS", chatGpt.merchant)
        assertEquals(11000L, chatGpt.lastAmount)
        assertEquals(10000L, chatGpt.previousAmount)
        assertTrue(chatGpt.isActive)

        // Pausar assinatura
        repo.setRecurringChargeActive(chatGpt.id, false)
        val updated = fakeDao.getAllRecurringChargesSnapshot().first()
        assertFalse(updated.isActive)

        // Reativar
        repo.setRecurringChargeActive(chatGpt.id, true)
        val reactivated = fakeDao.getAllRecurringChargesSnapshot().first()
        assertTrue(reactivated.isActive)
    }
}
