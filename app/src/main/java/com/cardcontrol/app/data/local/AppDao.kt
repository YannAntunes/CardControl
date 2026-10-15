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

    @Query("SELECT * FROM credit_cards WHERE lastFourDigits = :lastFour LIMIT 1")
    suspend fun getCardByLastFour(lastFour: String): CreditCardEntity?

    @Query("SELECT * FROM credit_cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: Long): CreditCardEntity?

    @Query("SELECT * FROM credit_cards LIMIT 1")
    suspend fun getFirstCard(): CreditCardEntity?

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

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM installments WHERE holderId = :holderId")
    suspend fun getTotalHistoricalSpentByHolder(holderId: Long): Double

    @Query("SELECT COALESCE(SUM(amountPaid), 0.0) FROM payments WHERE holderId = :holderId")
    suspend fun getTotalHistoricalPaidByHolder(holderId: Long): Double
}
