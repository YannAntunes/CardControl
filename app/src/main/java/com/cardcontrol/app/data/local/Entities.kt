package com.cardcontrol.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "holders")
data class HolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val whatsapp: String,
    val email: String = "",
    val subLimit: Long = 0L,
    val notifyByEmailOnPurchase: Boolean = true
)

@Entity(
    tableName = "credit_cards",
    foreignKeys = [
        ForeignKey(
            entity = HolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["defaultHolderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("defaultHolderId"), Index("lastFourDigits")]
)
data class CreditCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lastFourDigits: String,
    val totalLimit: Long,
    val closingDay: Int,
    val dueDay: Int,
    val defaultHolderId: Long?
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = CreditCardEntity::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = HolderEntity::class, parentColumns = ["id"], childColumns = ["holderId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("cardId"), Index("holderId"), Index("dedupHash")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val holderId: Long,
    val merchant: String,
    val totalAmount: Long,
    val installmentsCount: Int = 1,
    val purchaseTimestamp: Long = System.currentTimeMillis(),
    val isAutoCaptured: Boolean = false,
    val dedupHash: String = ""
)

@Entity(
    tableName = "installments",
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = HolderEntity::class, parentColumns = ["id"], childColumns = ["holderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CreditCardEntity::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("transactionId"), Index("holderId"), Index("cardId"), Index("refMonth", "refYear")]
)
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val holderId: Long,
    val cardId: Long,
    val merchantSnapshot: String,
    val installmentNumber: Int,
    val totalInstallments: Int,
    val amount: Long,
    val refMonth: Int,
    val refYear: Int,
    val purchaseTimestamp: Long
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(entity = HolderEntity::class, parentColumns = ["id"], childColumns = ["holderId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("holderId"), Index("refMonth", "refYear")]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val holderId: Long,
    val cardId: Long? = null,
    val amountPaid: Long,
    val refMonth: Int,
    val refYear: Int,
    val paymentTimestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)
