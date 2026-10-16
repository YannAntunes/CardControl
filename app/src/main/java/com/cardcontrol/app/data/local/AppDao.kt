package com.cardcontrol.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolder(holder: HolderEntity): Long

    @Query("SELECT * FROM holders ORDER BY name ASC")
    fun observeHolders(): Flow<List<HolderEntity>>

    @Query("SELECT * FROM holders WHERE id = :id LIMIT 1")
    suspend fun getHolderById(id: Long): HolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CreditCardEntity): Long

    @Query("SELECT * FROM credit_cards ORDER BY name ASC")
    fun observeCards(): Flow<List<CreditCardEntity>>

    @Query("SELECT * FROM credit_cards ORDER BY id ASC")
    suspend fun getAllCardsSnapshot(): List<CreditCardEntity>

    @Query("SELECT * FROM credit_cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: Long): CreditCardEntity?

    @Insert
    suspend fun insertTransaction(tx: TransactionEntity): Long

    @Insert
    suspend fun insertInstallments(installments: List<InstallmentEntity>)

    @Transaction
    suspend fun insertTransactionWithInstallments(
        tx: TransactionEntity,
        buildInstallments: (Long) -> List<InstallmentEntity>
    ): Long {
        val txId = insertTransaction(tx)
        insertInstallments(buildInstallments(txId))
        return txId
    }

    @Query("SELECT COUNT(*) FROM transactions WHERE dedupHash = :hash AND purchaseTimestamp >= :minTimestamp")
    suspend fun countRecentDuplicate(hash: String, minTimestamp: Long): Int

    @Query("SELECT * FROM installments WHERE refMonth = :month AND refYear = :year ORDER BY purchaseTimestamp DESC")
    fun observeInstallmentsByPeriod(month: Int, year: Int): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments")
    fun observeAllInstallments(): Flow<List<InstallmentEntity>>

    @Insert
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("SELECT * FROM payments WHERE refMonth = :month AND refYear = :year ORDER BY paymentTimestamp DESC")
    fun observePaymentsByPeriod(month: Int, year: Int): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments")
    fun observeAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM installments WHERE holderId = :holderId")
    suspend fun getTotalHistoricalSpentByHolder(holderId: Long): Long

    @Query("SELECT COALESCE(SUM(amountPaid), 0) FROM payments WHERE holderId = :holderId")
    suspend fun getTotalHistoricalPaidByHolder(holderId: Long): Long

    // --- Detector de Assinaturas e Recorrências ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringCharge(charge: RecurringChargeEntity): Long

    @Query("SELECT * FROM recurring_charges WHERE isActive = 1 ORDER BY merchant ASC")
    fun observeActiveRecurringCharges(): Flow<List<RecurringChargeEntity>>

    @Query("SELECT * FROM recurring_charges ORDER BY merchant ASC")
    fun observeAllRecurringCharges(): Flow<List<RecurringChargeEntity>>

    @Query("SELECT * FROM recurring_charges ORDER BY id ASC")
    suspend fun getAllRecurringChargesSnapshot(): List<RecurringChargeEntity>

    @Query("SELECT * FROM recurring_charges WHERE merchant = :merchant AND cardId = :cardId LIMIT 1")
    suspend fun getRecurringChargeByMerchantAndCard(merchant: String, cardId: Long): RecurringChargeEntity?

    @Query("UPDATE recurring_charges SET isActive = :isActive WHERE id = :id")
    suspend fun setRecurringChargeActive(id: Long, isActive: Boolean)

    @Query("UPDATE transactions SET isRecurring = :isRecurring WHERE id = :transactionId")
    suspend fun setTransactionRecurring(transactionId: Long, isRecurring: Boolean)

    @Query("SELECT * FROM transactions WHERE merchant = :merchant ORDER BY purchaseTimestamp ASC")
    suspend fun getTransactionsByMerchant(merchant: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY purchaseTimestamp ASC")
    suspend fun getAllTransactionsSnapshot(): List<TransactionEntity>

    @Query("SELECT COALESCE(SUM(expectedAmount), 0) FROM recurring_charges WHERE isActive = 1")
    suspend fun getTotalActiveRecurringAmount(): Long
}
