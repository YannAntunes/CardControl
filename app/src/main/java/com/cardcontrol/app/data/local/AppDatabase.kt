package com.cardcontrol.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HolderEntity::class,
        CreditCardEntity::class,
        TransactionEntity::class,
        InstallmentEntity::class,
        PaymentEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `holders_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `whatsapp` TEXT NOT NULL, `email` TEXT NOT NULL, `subLimit` INTEGER NOT NULL, `notifyByEmailOnPurchase` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `holders_new` (`id`, `name`, `whatsapp`, `email`, `subLimit`, `notifyByEmailOnPurchase`) SELECT `id`, `name`, `whatsapp`, `email`, CAST(ROUND(`subLimit` * 100.0) AS INTEGER), `notifyByEmailOnPurchase` FROM `holders`")
                db.execSQL("DROP TABLE `holders`")
                db.execSQL("ALTER TABLE `holders_new` RENAME TO `holders`")

                db.execSQL("CREATE TABLE IF NOT EXISTS `credit_cards_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `lastFourDigits` TEXT NOT NULL, `totalLimit` INTEGER NOT NULL, `closingDay` INTEGER NOT NULL, `dueDay` INTEGER NOT NULL, `defaultHolderId` INTEGER, FOREIGN KEY(`defaultHolderId`) REFERENCES `holders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
                db.execSQL("INSERT INTO `credit_cards_new` (`id`, `name`, `lastFourDigits`, `totalLimit`, `closingDay`, `dueDay`, `defaultHolderId`) SELECT `id`, `name`, `lastFourDigits`, CAST(ROUND(`totalLimit` * 100.0) AS INTEGER), `closingDay`, `dueDay`, `defaultHolderId` FROM `credit_cards`")
                db.execSQL("DROP TABLE `credit_cards`")
                db.execSQL("ALTER TABLE `credit_cards_new` RENAME TO `credit_cards`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_cards_defaultHolderId` ON `credit_cards` (`defaultHolderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_cards_lastFourDigits` ON `credit_cards` (`lastFourDigits`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `transactions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cardId` INTEGER NOT NULL, `holderId` INTEGER NOT NULL, `merchant` TEXT NOT NULL, `totalAmount` INTEGER NOT NULL, `installmentsCount` INTEGER NOT NULL, `purchaseTimestamp` INTEGER NOT NULL, `isAutoCaptured` INTEGER NOT NULL, `dedupHash` TEXT NOT NULL, FOREIGN KEY(`cardId`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`holderId`) REFERENCES `holders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `transactions_new` (`id`, `cardId`, `holderId`, `merchant`, `totalAmount`, `installmentsCount`, `purchaseTimestamp`, `isAutoCaptured`, `dedupHash`) SELECT `id`, `cardId`, `holderId`, `merchant`, CAST(ROUND(`totalAmount` * 100.0) AS INTEGER), `installmentsCount`, `purchaseTimestamp`, `isAutoCaptured`, `dedupHash` FROM `transactions`")
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_cardId` ON `transactions` (`cardId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_holderId` ON `transactions` (`holderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_dedupHash` ON `transactions` (`dedupHash`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `installments_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL, `holderId` INTEGER NOT NULL, `cardId` INTEGER NOT NULL, `merchantSnapshot` TEXT NOT NULL, `installmentNumber` INTEGER NOT NULL, `totalInstallments` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `refMonth` INTEGER NOT NULL, `refYear` INTEGER NOT NULL, `purchaseTimestamp` INTEGER NOT NULL, FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`holderId`) REFERENCES `holders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`cardId`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `installments_new` (`id`, `transactionId`, `holderId`, `cardId`, `merchantSnapshot`, `installmentNumber`, `totalInstallments`, `amount`, `refMonth`, `refYear`, `purchaseTimestamp`) SELECT `id`, `transactionId`, `holderId`, `cardId`, `merchantSnapshot`, `installmentNumber`, `totalInstallments`, CAST(ROUND(`amount` * 100.0) AS INTEGER), `refMonth`, `refYear`, `purchaseTimestamp` FROM `installments`")
                db.execSQL("DROP TABLE `installments`")
                db.execSQL("ALTER TABLE `installments_new` RENAME TO `installments`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_installments_transactionId` ON `installments` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_installments_holderId` ON `installments` (`holderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_installments_cardId` ON `installments` (`cardId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_installments_refMonth_refYear` ON `installments` (`refMonth`, `refYear`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `payments_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `holderId` INTEGER NOT NULL, `cardId` INTEGER, `amountPaid` INTEGER NOT NULL, `refMonth` INTEGER NOT NULL, `refYear` INTEGER NOT NULL, `paymentTimestamp` INTEGER NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`holderId`) REFERENCES `holders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `payments_new` (`id`, `holderId`, `cardId`, `amountPaid`, `refMonth`, `refYear`, `paymentTimestamp`, `notes`) SELECT `id`, `holderId`, `cardId`, CAST(ROUND(`amountPaid` * 100.0) AS INTEGER), `refMonth`, `refYear`, `paymentTimestamp`, `notes` FROM `payments`")
                db.execSQL("DROP TABLE `payments`")
                db.execSQL("ALTER TABLE `payments_new` RENAME TO `payments`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_holderId` ON `payments` (`holderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_refMonth_refYear` ON `payments` (`refMonth`, `refYear`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "card_control_offline.db"
                ).addMigrations(MIGRATION_2_3).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
