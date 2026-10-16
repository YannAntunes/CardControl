package com.cardcontrol.app

import com.cardcontrol.app.data.local.RecurringChargeEntity
import com.cardcontrol.app.data.local.TransactionEntity
import com.cardcontrol.app.util.SubscriptionTracker
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class SubscriptionTrackerTest {

    @Test
    fun `detecta assinatura com cadencia de aproximadamente 30 dias`() {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - TimeUnit.DAYS.toMillis(30)

        val tx1 = TransactionEntity(
            id = 1L,
            cardId = 10L,
            holderId = 100L,
            merchant = "NETFLIX.COM",
            totalAmount = 5590L,
            purchaseTimestamp = thirtyDaysAgo
        )
        val tx2 = TransactionEntity(
            id = 2L,
            cardId = 10L,
            holderId = 100L,
            merchant = "Netflix.com",
            totalAmount = 5590L,
            purchaseTimestamp = now
        )

        val detected = SubscriptionTracker.detectSubscriptions(listOf(tx1, tx2))
        assertEquals(1, detected.size)

        val sub = detected.first()
        assertTrue(sub.merchant.equals("NETFLIX.COM", ignoreCase = true))
        assertEquals(5590L, sub.lastAmount)
        assertEquals(5590L, sub.expectedAmount)
        assertEquals(30, sub.billingCycleDays)
        assertFalse(sub.hasPriceCreep)
    }

    @Test
    fun `detecta variacao de preco (price creep) quando valor mais recente aumenta`() {
        val now = System.currentTimeMillis()
        val twentyNineDaysAgo = now - TimeUnit.DAYS.toMillis(29)

        val tx1 = TransactionEntity(
            id = 1L,
            cardId = 10L,
            holderId = 100L,
            merchant = "SPOTIFY",
            totalAmount = 2190L,
            purchaseTimestamp = twentyNineDaysAgo
        )
        val tx2 = TransactionEntity(
            id = 2L,
            cardId = 10L,
            holderId = 100L,
            merchant = "SPOTIFY",
            totalAmount = 2790L,
            purchaseTimestamp = now
        )

        val detected = SubscriptionTracker.detectSubscriptions(listOf(tx1, tx2))
        assertEquals(1, detected.size)

        val sub = detected.first()
        assertEquals("SPOTIFY", sub.merchant)
        assertEquals(2790L, sub.lastAmount)
        assertEquals(2190L, sub.previousAmount)
        assertTrue(sub.hasPriceCreep)
        assertEquals(600L, sub.priceCreepDelta)
    }

    @Test
    fun `ignora compras avulsas e intervalos irregulares fora da janela de recorrencia`() {
        val now = System.currentTimeMillis()
        val fiveDaysAgo = now - TimeUnit.DAYS.toMillis(5)

        val tx1 = TransactionEntity(
            id = 1L,
            cardId = 10L,
            holderId = 100L,
            merchant = "IFOOD RESTAURANTE",
            totalAmount = 4500L,
            purchaseTimestamp = fiveDaysAgo
        )
        val tx2 = TransactionEntity(
            id = 2L,
            cardId = 10L,
            holderId = 100L,
            merchant = "IFOOD RESTAURANTE",
            totalAmount = 7200L,
            purchaseTimestamp = now
        )

        val detected = SubscriptionTracker.detectSubscriptions(listOf(tx1, tx2))
        assertTrue("Compras em intervalo de 5 dias não devem ser classificadas como assinatura mensal", detected.isEmpty())
    }

    @Test
    fun `marca transacao avulsa como assinatura se isRecurring for verdadeiro manualmente`() {
        val tx = TransactionEntity(
            id = 1L,
            cardId = 10L,
            holderId = 100L,
            merchant = "ACADEMIA SMART FIT",
            totalAmount = 11990L,
            purchaseTimestamp = System.currentTimeMillis(),
            isRecurring = true
        )

        val detected = SubscriptionTracker.detectSubscriptions(listOf(tx))
        assertEquals(1, detected.size)

        val sub = detected.first()
        assertEquals("ACADEMIA SMART FIT", sub.merchant)
        assertEquals(11990L, sub.expectedAmount)
        assertEquals(30, sub.billingCycleDays)
        assertTrue(sub.isUserConfirmed)
    }

    @Test
    fun `calcula custos fixos mensais comprometidos considerando apenas ativas`() {
        val list = listOf(
            RecurringChargeEntity(id = 1L, cardId = 1L, holderId = 1L, merchant = "NETFLIX", expectedAmount = 5590L, lastAmount = 5590L, isActive = true),
            RecurringChargeEntity(id = 2L, cardId = 1L, holderId = 1L, merchant = "SPOTIFY", expectedAmount = 2190L, lastAmount = 2190L, isActive = true),
            RecurringChargeEntity(id = 3L, cardId = 1L, holderId = 1L, merchant = "OLD SERVICE", expectedAmount = 4000L, lastAmount = 4000L, isActive = false)
        )

        val total = SubscriptionTracker.calculateCommittedFixedCosts(list)
        assertEquals(7780L, total)
    }

    @Test
    fun `calcula impacto no limite com precisao e trata limites zerados`() {
        assertEquals(10.0, SubscriptionTracker.calculateLimitImpactPercent(10000L, 100000L), 0.001)
        assertEquals(25.5, SubscriptionTracker.calculateLimitImpactPercent(25500L, 100000L), 0.001)
        assertEquals(0.0, SubscriptionTracker.calculateLimitImpactPercent(5000L, 0L), 0.001)
        assertEquals(0.0, SubscriptionTracker.calculateLimitImpactPercent(5000L, -100L), 0.001)
    }
}
