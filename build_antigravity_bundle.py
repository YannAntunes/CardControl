#!/usr/bin/env python3
import os
import zipfile

PROJECT_ROOT = "CardControl"
ZIP_NAME = "CardControl_v2_1.zip"

FILES = {
    "ANTIGRAVITY_GOAL.md": """# GOAL: Implantação, Testes e Build do CardControl v2.1

## Contexto
Projeto Android nativo (Kotlin + Jetpack Compose + Room + Keystore) 100% offline-first.

## Etapas de Execução Obrigatórias
1. **Ambiente e Wrapper:** Verifique o `ANDROID_HOME` (API 34) e JDK 17. Caso não exista `gradlew`, gere com `gradle wrapper --gradle-version 8.7`.
2. **Harness de Segurança e Domínio:** Execute `./gradlew testDebugUnitTest` e valide todas as asserções da suíte `SecurityAndDomainTestHarness`.
3. **Compilação do Binário:** Execute `./gradlew assembleDebug` e confirme a geração de `app/build/outputs/apk/debug/app-debug.apk`.
4. **Relatório:** Apresente o resultado real do terminal para os testes e build, sem suposições.
""",
    "gradle.properties": """org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
""",
    "settings.gradle.kts": """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "CardControl"
include(":app")
""",
    "build.gradle.kts": """plugins {
    id("com.android.application") version "8.4.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
""",
    "app/build.gradle.kts": """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.cardcontrol.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cardcontrol.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "2.1.0"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,NOTICE.md,LICENSE.md,LICENSE.txt,NOTICE.txt}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    testImplementation("junit:junit:4.13.2")
}
""",
    "app/src/main/AndroidManifest.xml": """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:allowBackup="false"
        android:label="CardControl"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service
            android:name=".service.BankNotificationListener"
            android:exported="true"
            android:label="Leitor Automatico CardControl"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.provider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
""",
    "app/src/main/res/xml/file_paths.xml": """<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="invoices" path="invoices/" />
</paths>
""",
    "app/src/main/java/com/cardcontrol/app/security/SecuritySanitizer.kt": """package com.cardcontrol.app.security

object SecuritySanitizer {
    private const val MAX_NOTIFICATION_LENGTH = 1000
    private val SAFE_FILENAME_REGEX = Regex(\"\"\"[^a-zA-Z0-9_\\-]\"\"\")
    private val CRLF_REGEX = Regex(\"\"\"[\\r\\n\\t]+\"\"\")

    fun boundNotificationInput(raw: String): String = raw.take(MAX_NOTIFICATION_LENGTH).trim()

    fun sanitizeHeaderField(input: String, maxLength: Int = 80): String {
        return input.replace(CRLF_REGEX, " ")
            .replace(Regex(\"\"\"\\s+\"\"\"), " ")
            .trim()
            .take(maxLength)
    }

    fun sanitizeFileNameSegment(input: String): String {
        val cleaned = input.trim()
            .replace(Regex(\"\"\"\\s+\"\"\"), "_")
            .replace(SAFE_FILENAME_REGEX, "")
            .take(40)
        return cleaned.ifBlank { "Titular" }
    }

    fun validateFinancialAmount(amount: Double): Double {
        require(!amount.isNaN() && !amount.isInfinite() && amount > 0.0 && amount <= 1_000_000.0) {
            "Valor financeiro fora dos limites seguros."
        }
        return amount
    }

    fun validateInstallments(count: Int): Int = count.coerceIn(1, 72)
}
""",
    "app/src/main/java/com/cardcontrol/app/data/local/Entities.kt": """package com.cardcontrol.app.data.local

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
    val subLimit: Double = 0.0,
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
    val totalLimit: Double,
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
    val totalAmount: Double,
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
    val amount: Double,
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
    val amountPaid: Double,
    val refMonth: Int,
    val refYear: Int,
    val paymentTimestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)
""",
    "app/src/main/java/com/cardcontrol/app/data/local/AppDao.kt": """package com.cardcontrol.app.data.local

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
""",
    "app/src/main/java/com/cardcontrol/app/data/local/AppDatabase.kt": """package com.cardcontrol.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HolderEntity::class,
        CreditCardEntity::class,
        TransactionEntity::class,
        InstallmentEntity::class,
        PaymentEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "card_control_offline.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
""",
    "app/src/main/java/com/cardcontrol/app/data/security/CredentialStore.kt": """package com.cardcontrol.app.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {
    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "card_control_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSmtpCredentials(senderEmail: String, appPassword: String) {
        prefs.edit()
            .putString(KEY_EMAIL, senderEmail.trim())
            .putString(KEY_PASSWORD, appPassword.replace(" ", ""))
            .apply()
    }

    fun getSenderEmail(): String = prefs.getString(KEY_EMAIL, "") ?: ""
    fun getAppPassword(): String = prefs.getString(KEY_PASSWORD, "") ?: ""
    fun isConfigured(): Boolean = getSenderEmail().isNotBlank() && getAppPassword().isNotBlank()

    companion object {
        private const val KEY_EMAIL = "smtp_sender_email"
        private const val KEY_PASSWORD = "smtp_app_password"
    }
}
""",
    "app/src/main/java/com/cardcontrol/app/data/repository/CardRepository.kt": """package com.cardcontrol.app.data.repository

import com.cardcontrol.app.data.local.*
import com.cardcontrol.app.security.SecuritySanitizer
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CardRepository(private val dao: AppDao) {
    val holders = dao.observeHolders()
    val cards = dao.observeCards()
    val allInstallments = dao.observeAllInstallments()
    val allPayments = dao.observeAllPayments()

    fun observeInstallmentsForMonth(month: Int, year: Int) = dao.observeInstallmentsByPeriod(month, year)
    fun observePaymentsForMonth(month: Int, year: Int) = dao.observePaymentsByPeriod(month, year)

    suspend fun addHolder(name: String, whatsapp: String, email: String, subLimit: Double) {
        dao.insertHolder(
            HolderEntity(
                name = SecuritySanitizer.sanitizeHeaderField(name, 50),
                whatsapp = whatsapp.filter { it.isDigit() }.take(15),
                email = email.trim().take(80),
                subLimit = subLimit.coerceAtLeast(0.0)
            )
        )
    }

    suspend fun addCard(
        name: String,
        lastFour: String,
        totalLimit: Double,
        closingDay: Int,
        dueDay: Int,
        defaultHolderId: Long?
    ) {
        dao.insertCard(
            CreditCardEntity(
                name = SecuritySanitizer.sanitizeHeaderField(name, 40),
                lastFourDigits = lastFour.filter { it.isDigit() }.takeLast(4),
                totalLimit = totalLimit.coerceAtLeast(0.0),
                closingDay = closingDay.coerceIn(1, 28),
                dueDay = dueDay.coerceIn(1, 28),
                defaultHolderId = defaultHolderId
            )
        )
    }

    suspend fun isDuplicateNotification(dedupHash: String, timestamp: Long): Boolean {
        if (dedupHash.isBlank()) return false
        return dao.countRecentDuplicate(dedupHash, timestamp - 120_000L) > 0
    }

    suspend fun registerExpense(
        cardId: Long,
        holderId: Long,
        merchant: String,
        totalAmount: Double,
        installmentsCount: Int,
        purchaseTimestamp: Long = System.currentTimeMillis(),
        isAutoCaptured: Boolean = false,
        dedupHash: String = ""
    ): Long {
        val card = dao.getCardById(cardId) ?: throw IllegalArgumentException("Cartao nao encontrado")
        val validAmount = SecuritySanitizer.validateFinancialAmount(totalAmount)
        val safeInstallments = SecuritySanitizer.validateInstallments(installmentsCount)
        val safeMerchant = SecuritySanitizer.sanitizeHeaderField(merchant, 40)

        val purchaseDate: LocalDate = Instant.ofEpochMilli(purchaseTimestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val startYearMonth = calculateFirstInstallmentMonth(purchaseDate, card.closingDay)
        val amounts = splitInstallmentAmounts(validAmount, safeInstallments)

        val tx = TransactionEntity(
            cardId = cardId,
            holderId = holderId,
            merchant = safeMerchant,
            totalAmount = validAmount,
            installmentsCount = safeInstallments,
            purchaseTimestamp = purchaseTimestamp,
            isAutoCaptured = isAutoCaptured,
            dedupHash = dedupHash
        )

        return dao.insertTransactionWithInstallments(tx) { txId ->
            amounts.mapIndexed { index, parcelAmount ->
                val targetDate = startYearMonth.plusMonths(index.toLong())
                InstallmentEntity(
                    transactionId = txId,
                    holderId = holderId,
                    cardId = cardId,
                    merchantSnapshot = safeMerchant,
                    installmentNumber = index + 1,
                    totalInstallments = safeInstallments,
                    amount = parcelAmount,
                    refMonth = targetDate.monthValue,
                    refYear = targetDate.year,
                    purchaseTimestamp = purchaseTimestamp
                )
            }
        }
    }

    suspend fun registerPayment(
        holderId: Long,
        cardId: Long?,
        amountPaid: Double,
        refMonth: Int,
        refYear: Int,
        notes: String
    ) {
        val validAmount = SecuritySanitizer.validateFinancialAmount(amountPaid)
        dao.insertPayment(
            PaymentEntity(
                holderId = holderId,
                cardId = cardId,
                amountPaid = validAmount,
                refMonth = refMonth,
                refYear = refYear,
                notes = SecuritySanitizer.sanitizeHeaderField(notes, 80)
            )
        )
    }

    companion object {
        fun calculateFirstInstallmentMonth(purchaseDate: LocalDate, closingDay: Int): LocalDate {
            val baseMonth = LocalDate.of(purchaseDate.year, purchaseDate.monthValue, 1)
            return if (purchaseDate.dayOfMonth >= closingDay) baseMonth.plusMonths(1) else baseMonth
        }

        fun splitInstallmentAmounts(totalAmount: Double, count: Int): List<Double> {
            val safeCount = SecuritySanitizer.validateInstallments(count)
            val totalBd = BigDecimal.valueOf(totalAmount).setScale(2, RoundingMode.HALF_UP)
            val countBd = BigDecimal.valueOf(safeCount.toLong())
            val baseValue = totalBd.divide(countBd, 2, RoundingMode.DOWN)
            val remainder = totalBd.subtract(baseValue.multiply(countBd))

            return List(safeCount) { index ->
                if (index == 0) baseValue.add(remainder).toDouble() else baseValue.toDouble()
            }
        }
    }
}
""",
    "app/src/main/java/com/cardcontrol/app/service/BankNotificationParser.kt": """package com.cardcontrol.app.service

import com.cardcontrol.app.security.SecuritySanitizer
import java.security.MessageDigest

data class ParsedBankExpense(
    val amount: Double,
    val merchant: String,
    val lastFourDigits: String?,
    val installments: Int,
    val dedupHash: String
)

object BankNotificationParser {
    val SUPPORTED_PACKAGES = setOf(
        "com.nu.production",
        "br.com.intermedium",
        "com.itau",
        "com.itaucard.activity",
        "com.c6bank.app",
        "com.santander.app",
        "br.com.santander.way",
        "com.bradesco",
        "br.com.bradesco.cartoes",
        "br.gov.caixa.cartoes",
        "com.picpay",
        "com.mercadopago.wallet"
    )

    private val APPROVAL_KEYWORDS = listOf(
        "compra aprovada", "compra no credito", "compra no crédito",
        "compra no cartao", "compra no cartão", "transacao aprovada",
        "transação aprovada", "você comprou", "voce comprou", "compra realizada"
    )

    private val REJECTION_OR_REFUND_KEYWORDS = listOf(
        "negada", "recusada", "estorno", "estornada", "cancelada",
        "reembolso", "fatura fechada", "pagamento recebido", "pix enviado"
    )

    private val AMOUNT_REGEX = Regex(\"\"\"R\\$\\s*([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{2}|[0-9]+,[0-9]{2})\"\"\", RegexOption.IGNORE_CASE)
    private val LAST_FOUR_REGEX = Regex(\"\"\"(?:final|cart[aã]o|\\*{2,4})\\s*([0-9]{4})\"\"\", RegexOption.IGNORE_CASE)
    private val INSTALLMENTS_REGEX = Regex(\"\"\"(?:em\\s+)?([0-9]{1,2})\\s*x\\b\"\"\", RegexOption.IGNORE_CASE)
    private val MERCHANT_REGEX = Regex(
        \"\"\"(?:em|no|na|estabelecimento:?)\\s+([A-Za-z0-9À-ÿ\\s\\*.\\-_&]{2,35}?)(?:\\s+em\\s+\\d|\\s+no\\s+valor|\\s+com\\s+cart|\\s+no\\s+cart|\\s+aprovad|\\.|,|$)\"\"\",
        RegexOption.IGNORE_CASE
    )

    fun parse(packageName: String, title: String, text: String, timestamp: Long): ParsedBankExpense? {
        if (packageName !in SUPPORTED_PACKAGES) return null

        val safeTitle = SecuritySanitizer.boundNotificationInput(title)
        val safeText = SecuritySanitizer.boundNotificationInput(text)
        val fullContent = "$safeTitle $safeText".trim()
        val normalized = fullContent.lowercase()

        if (REJECTION_OR_REFUND_KEYWORDS.any { normalized.contains(it) }) return null
        if (APPROVAL_KEYWORDS.none { normalized.contains(it) }) return null

        val amountMatch = AMOUNT_REGEX.find(fullContent)?.groupValues?.get(1) ?: return null
        val cleanAmount = amountMatch.replace(".", "").replace(",", ".").toDoubleOrNull() ?: return null
        if (cleanAmount <= 0.0 || cleanAmount > 1_000_000.0) return null

        val lastFour = LAST_FOUR_REGEX.find(fullContent)?.groupValues?.get(1)
        val rawInstallments = INSTALLMENTS_REGEX.find(fullContent)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val installments = SecuritySanitizer.validateInstallments(rawInstallments)

        val rawMerchant = MERCHANT_REGEX.find(fullContent)?.groupValues?.get(1)?.trim()
        val fallbackTitle = safeTitle.takeIf { it.isNotBlank() && !it.lowercase().contains("compra") }
        val merchant = SecuritySanitizer.sanitizeHeaderField(
            rawMerchant?.takeIf { it.isNotBlank() } ?: fallbackTitle ?: "Compra Automatica",
            maxLength = 40
        )

        val timeBucket = timestamp / 120_000L
        val hashInput = "$packageName|${lastFour ?: "0000"}|$cleanAmount|${merchant.lowercase()}|$timeBucket"

        return ParsedBankExpense(
            amount = cleanAmount,
            merchant = merchant,
            lastFourDigits = lastFour,
            installments = installments,
            dedupHash = sha256(hashInput)
        )
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }
}
""",
    "app/src/main/java/com/cardcontrol/app/service/BankNotificationListener.kt": """package com.cardcontrol.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.cardcontrol.app.data.local.AppDatabase
import com.cardcontrol.app.data.repository.CardRepository
import com.cardcontrol.app.data.security.CredentialStore
import com.cardcontrol.app.util.ReportEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BankNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg !in BankNotificationParser.SUPPORTED_PACKAGES) return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (sbn.isOngoing) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        val parsed = BankNotificationParser.parse(pkg, title, text, sbn.postTime) ?: return

        serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val dao = db.appDao()
            val repo = CardRepository(dao)

            if (repo.isDuplicateNotification(parsed.dedupHash, sbn.postTime)) return@launch

            val matchedCard = parsed.lastFourDigits?.let { dao.getCardByLastFour(it) }
                ?: dao.getFirstCard()
                ?: return@launch

            val holderId = matchedCard.defaultHolderId ?: return@launch
            val holder = dao.getHolderById(holderId) ?: return@launch

            repo.registerExpense(
                cardId = matchedCard.id,
                holderId = holder.id,
                merchant = parsed.merchant,
                totalAmount = parsed.amount,
                installmentsCount = parsed.installments,
                purchaseTimestamp = sbn.postTime,
                isAutoCaptured = true,
                dedupHash = parsed.dedupHash
            )

            if (holder.notifyByEmailOnPurchase && holder.email.isNotBlank()) {
                val creds = CredentialStore(applicationContext)
                if (creds.isConfigured()) {
                    val totalSpent = dao.getTotalHistoricalSpentByHolder(holder.id)
                    val totalPaid = dao.getTotalHistoricalPaidByHolder(holder.id)
                    val currentDebt = (totalSpent - totalPaid).coerceAtLeast(0.0)
                    val remainingSubLimit = holder.subLimit - currentDebt

                    ReportEngine.sendInstantPurchaseEmail(
                        senderEmail = creds.getSenderEmail(),
                        appPassword = creds.getAppPassword(),
                        recipientEmail = holder.email,
                        holderName = holder.name,
                        cardName = "${matchedCard.name} (Final ${matchedCard.lastFourDigits})",
                        merchant = parsed.merchant,
                        amount = parsed.amount,
                        installments = parsed.installments,
                        remainingSubLimit = remainingSubLimit
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
""",
    "app/src/main/java/com/cardcontrol/app/util/ReportEngine.kt": """package com.cardcontrol.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.cardcontrol.app.data.local.HolderEntity
import com.cardcontrol.app.data.local.InstallmentEntity
import com.cardcontrol.app.security.SecuritySanitizer
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object ReportEngine {
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    fun formatCurrency(value: Double): String = currencyFormatter.format(value)

    fun buildWhatsAppSummaryText(
        holder: HolderEntity,
        month: Int,
        year: Int,
        installments: List<InstallmentEntity>,
        paidInMonth: Double,
        globalCommitted: Double
    ): String {
        val totalMonth = installments.sumOf { it.amount }
        val netDue = (totalMonth - paidInMonth).coerceAtLeast(0.0)
        val availableSubLimit = holder.subLimit - globalCommitted

        return buildString {
            appendLine("*[CardControl] Fatura %02d/%d*".format(month, year))
            appendLine("Titular/Dependente: *${SecuritySanitizer.sanitizeHeaderField(holder.name)}*")
            appendLine("--------------------------------")
            if (installments.isEmpty()) {
                appendLine("Nenhum lancamento neste periodo.")
            } else {
                installments.forEach { item ->
                    val parcelTag = if (item.totalInstallments > 1) " (${item.installmentNumber}/${item.totalInstallments})" else ""
                    appendLine("- ${item.merchantSnapshot}$parcelTag: *${formatCurrency(item.amount)}*")
                }
            }
            appendLine("--------------------------------")
            appendLine("Total do Mes: *${formatCurrency(totalMonth)}*")
            appendLine("Pago no Mes: *${formatCurrency(paidInMonth)}*")
            appendLine("*Saldo a Pagar: ${formatCurrency(netDue)}*")
            if (holder.subLimit > 0) {
                appendLine("Sublimite Disponivel: *${formatCurrency(availableSubLimit)}*")
                if (availableSubLimit < 0) appendLine("[!] *ATENCAO: Sublimite excedido!*")
            }
        }
    }

    fun generatePdfReport(
        context: Context,
        holder: HolderEntity,
        month: Int,
        year: Int,
        installments: List<InstallmentEntity>,
        paidInMonth: Double,
        globalCommitted: Double
    ): File {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply { color = Color.rgb(20, 20, 20); textSize = 18f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { color = Color.rgb(50, 50, 50); textSize = 12f }
        val boldPaint = Paint().apply { color = Color.rgb(0, 0, 0); textSize = 13f; isFakeBoldText = true }

        var y = 50f
        canvas.drawText("RELATORIO DE FATURA - %02d/%d".format(month, year), 40f, y, titlePaint)
        y += 28f
        canvas.drawText("Responsavel: ${SecuritySanitizer.sanitizeHeaderField(holder.name)}", 40f, y, boldPaint)
        y += 20f

        val totalMonth = installments.sumOf { it.amount }
        val netDue = (totalMonth - paidInMonth).coerceAtLeast(0.0)
        val availableSubLimit = holder.subLimit - globalCommitted

        canvas.drawText("Total: ${formatCurrency(totalMonth)} | Pago: ${formatCurrency(paidInMonth)} | A Pagar: ${formatCurrency(netDue)}", 40f, y, bodyPaint)
        y += 18f
        if (holder.subLimit > 0) {
            canvas.drawText("Sublimite: ${formatCurrency(holder.subLimit)} | Disponivel: ${formatCurrency(availableSubLimit)}", 40f, y, bodyPaint)
            y += 24f
        }

        canvas.drawLine(40f, y, 555f, y, bodyPaint)
        y += 22f
        canvas.drawText("LANCAMENTOS DO PERIODO:", 40f, y, boldPaint)
        y += 22f

        installments.take(28).forEach { item ->
            val parcelInfo = if (item.totalInstallments > 1) " [${item.installmentNumber}/${item.totalInstallments}]" else " [A vista]"
            canvas.drawText("• ${SecuritySanitizer.sanitizeHeaderField(item.merchantSnapshot, 40)}$parcelInfo", 45f, y, bodyPaint)
            canvas.drawText(formatCurrency(item.amount), 460f, y, boldPaint)
            y += 18f
        }

        pdf.finishPage(page)

        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val safeHolderSegment = SecuritySanitizer.sanitizeFileNameSegment(holder.name)
        val file = File(dir, "Fatura_${safeHolderSegment}_%02d_%d.pdf".format(month, year))

        check(file.canonicalPath.startsWith(dir.canonicalPath)) { "Caminho de arquivo invalido" }

        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    fun sharePdfAndTextViaWhatsApp(
        context: Context,
        holder: HolderEntity,
        month: Int,
        year: Int,
        installments: List<InstallmentEntity>,
        paidInMonth: Double,
        globalCommitted: Double
    ) {
        val pdfFile = generatePdfReport(context, holder, month, year, installments, paidInMonth, globalCommitted)
        val summaryText = buildWhatsAppSummaryText(holder, month, year, installments, paidInMonth, globalCommitted)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, summaryText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val cleanPhone = holder.whatsapp.filter { it.isDigit() }
            if (cleanPhone.isNotBlank()) {
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
            }
            setPackage("com.whatsapp")
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Enviar Fatura").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            intent.setPackage(null)
            context.startActivity(Intent.createChooser(intent, "Compartilhar Fatura").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun sendInstantPurchaseEmail(
        senderEmail: String,
        appPassword: String,
        recipientEmail: String,
        holderName: String,
        cardName: String,
        merchant: String,
        amount: Double,
        installments: Int,
        remainingSubLimit: Double
    ) {
        try {
            val safeMerchant = SecuritySanitizer.sanitizeHeaderField(merchant, 50)
            val safeHolder = SecuritySanitizer.sanitizeHeaderField(holderName, 50)
            val safeCard = SecuritySanitizer.sanitizeHeaderField(cardName, 50)

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
                put("mail.smtp.ssl.checkserveridentity", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "10000")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(senderEmail, appPassword)
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(senderEmail, "CardControl Automatico"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail, true))
                subject = "[CardControl] Nova Compra: $safeMerchant"
                val statusAlert = if (remainingSubLimit < 0) "\\n[!] ATENCAO: Seu sublimite foi ultrapassado!" else ""
                setText(
                    \"\"\"
                    Ola, $safeHolder!
                    
                    Compra registrada no cartao $safeCard:
                    • Estabelecimento: $safeMerchant
                    • Valor Total: ${formatCurrency(amount)}
                    • Parcelas: ${installments}x
                    • Limite Restante: ${formatCurrency(remainingSubLimit)}
                    $statusAlert
                    \"\"\".trimIndent(),
                    "UTF-8"
                )
            }
            Transport.send(message)
        } catch (_: Exception) {
        }
    }
}
""",
    "app/src/main/java/com/cardcontrol/app/ui/MainViewModel.kt": """package com.cardcontrol.app.ui

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
    val monthlySpent: Double,
    val monthlyPaid: Double,
    val monthlyNetDue: Double,
    val globalCommitted: Double,
    val availableSubLimit: Double
)

data class CardSummaryUi(
    val card: CreditCardEntity,
    val globalCommitted: Double,
    val availableLimit: Double
)

data class DashboardUiState(
    val selectedMonth: Int = LocalDate.now().monthValue,
    val selectedYear: Int = LocalDate.now().year,
    val holders: List<HolderEntity> = emptyList(),
    val cards: List<CreditCardEntity> = emptyList(),
    val holderSummaries: List<HolderSummaryUi> = emptyList(),
    val cardSummaries: List<CardSummaryUi> = emptyList(),
    val totalMonthInvoice: Double = 0.0,
    val totalMonthPaid: Double = 0.0
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
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
            val gCommitted = (gSpent - gPaid).coerceAtLeast(0.0)

            HolderSummaryUi(
                holder = h,
                monthlyInstallments = hMonthInst,
                monthlySpent = mSpent,
                monthlyPaid = mPaid,
                monthlyNetDue = (mSpent - mPaid).coerceAtLeast(0.0),
                globalCommitted = gCommitted,
                availableSubLimit = h.subLimit - gCommitted
            )
        }

        val cardSummaries = cards.map { c ->
            val cSpent = allInst.filter { it.cardId == c.id }.sumOf { it.amount }
            val cPaid = allPay.filter { it.cardId == c.id }.sumOf { it.amountPaid }
            val cCommitted = (cSpent - cPaid).coerceAtLeast(0.0)
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
    fun addHolder(name: String, whatsapp: String, email: String, subLimit: Double) {
        viewModelScope.launch { repository.addHolder(name, whatsapp, email, subLimit) }
    }
    fun addCard(name: String, lastFour: String, limit: Double, closing: Int, due: Int, defaultHolderId: Long?) {
        viewModelScope.launch { repository.addCard(name, lastFour, limit, closing, due, defaultHolderId) }
    }
    fun addExpense(cardId: Long, holderId: Long, merchant: String, amount: Double, installments: Int) {
        viewModelScope.launch { repository.registerExpense(cardId, holderId, merchant, amount, installments) }
    }
    fun addPayment(holderId: Long, cardId: Long?, amount: Double, notes: String) {
        val period = selectedPeriod.value
        viewModelScope.launch { repository.registerPayment(holderId, cardId, amount, period.monthValue, period.year, notes) }
    }
}
""",
    "app/src/main/java/com/cardcontrol/app/ui/theme/LiquidGlass.kt": """package com.cardcontrol.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GlassPalette {
    val ObsidianBg = Color(0xFF070A12)
    val DeepSlateBg = Color(0xFF0D1322)
    val CyanGlow = Color(0xFF38BDF8)
    val IndigoGlow = Color(0xFF6366F1)
    val EmeraldAccent = Color(0xFF34D399)
    val CoralDanger = Color(0xFFFB7185)
    val AmberWarn = Color(0xFFFBBF24)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val SurfaceTintTop = Color(0x28FFFFFF)
    val SurfaceTintBottom = Color(0x0DFFFFFF)
    val SpecularHighlight = Color(0x66FFFFFF)
    val SpecularShadow = Color(0x14FFFFFF)
}

fun Modifier.liquidGlassSurface(
    cornerRadius: Dp = 22.dp,
    accentTint: Color = GlassPalette.CyanGlow
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    GlassPalette.SurfaceTintTop,
                    accentTint.copy(alpha = 0.06f),
                    GlassPalette.SurfaceTintBottom
                ),
                start = Offset(0f, 0f),
                end = Offset(900f, 900f)
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    GlassPalette.SpecularHighlight,
                    accentTint.copy(alpha = 0.28f),
                    GlassPalette.SpecularShadow
                ),
                start = Offset(0f, 0f),
                end = Offset(600f, 600f)
            ),
            shape = shape
        )
}

@Composable
fun LiquidAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GlassPalette.ObsidianBg, GlassPalette.DeepSlateBg)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlassPalette.CyanGlow.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.12f),
                    radius = size.minDimension * 0.65f
                ),
                center = Offset(size.width * 0.15f, size.height * 0.12f),
                radius = size.minDimension * 0.65f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlassPalette.IndigoGlow.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.48f),
                    radius = size.minDimension * 0.70f
                ),
                center = Offset(size.width * 0.88f, size.height * 0.48f),
                radius = size.minDimension * 0.70f
            )
        }
        content()
    }
}

@Composable
fun LiquidGlassBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accentColor: Color = GlassPalette.CyanGlow
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.75f), accentColor)))
        )
    }
}

@Composable
fun LiquidPillSelector(
    options: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (id, label) ->
            val isSelected = id == selectedId
            val accent = if (isSelected) GlassPalette.CyanGlow else GlassPalette.TextSecondary
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) GlassPalette.CyanGlow.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                    .border(
                        width = 1.dp,
                        color = if (isSelected) GlassPalette.CyanGlow else Color.White.copy(alpha = 0.12f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) GlassPalette.TextPrimary else accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
""",
    "app/src/main/java/com/cardcontrol/app/ui/MainScreen.kt": """package com.cardcontrol.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardcontrol.app.ui.theme.*
import com.cardcontrol.app.util.ReportEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenNotificationSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var expandedHolderId by remember { mutableStateOf<Long?>(null) }

    var showExpenseDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showHolderDialog by remember { mutableStateOf(false) }
    var showCardDialog by remember { mutableStateOf(false) }

    LiquidAmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .liquidGlassSurface(cornerRadius = 18.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CardControl", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = GlassPalette.TextPrimary)
                            Text("Motor Offline • Liquid Glass", style = MaterialTheme.typography.labelSmall, color = GlassPalette.CyanGlow)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.changeMonth(-1) }) { Icon(Icons.Default.ChevronLeft, null, tint = GlassPalette.TextPrimary) }
                            Text("%02d/%d".format(state.selectedMonth, state.selectedYear), fontWeight = FontWeight.Bold, color = GlassPalette.TextPrimary)
                            IconButton(onClick = { viewModel.changeMonth(1) }) { Icon(Icons.Default.ChevronRight, null, tint = GlassPalette.TextPrimary) }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = GlassPalette.DeepSlateBg.copy(alpha = 0.88f), tonalElevation = 0.dp) {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.ReceiptLong, null) }, label = { Text("Faturas") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.CreditCard, null) }, label = { Text("Cartões") })
                    NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Security, null) }, label = { Text("Automação") })
                }
            },
            floatingActionButton = {
                if (selectedTab == 0 && state.cards.isNotEmpty() && state.holders.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SmallFloatingActionButton(
                            onClick = { showPaymentDialog = true },
                            containerColor = GlassPalette.EmeraldAccent.copy(alpha = 0.85f),
                            contentColor = GlassPalette.ObsidianBg
                        ) { Icon(Icons.Default.Payments, contentDescription = "Abater Valor") }
                        ExtendedFloatingActionButton(
                            onClick = { showExpenseDialog = true },
                            containerColor = GlassPalette.CyanGlow,
                            contentColor = GlassPalette.ObsidianBg,
                            icon = { Icon(Icons.Default.Add, null) },
                            text = { Text("Lançar Compra", fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                when (selectedTab) {
                    0 -> {
                        val remainingInvoice = (state.totalMonthInvoice - state.totalMonthPaid).coerceAtLeast(0.0)
                        val paidRatio = if (state.totalMonthInvoice > 0) (state.totalMonthPaid / state.totalMonthInvoice).toFloat() else 0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassSurface(cornerRadius = 24.dp, accentTint = GlassPalette.CyanGlow)
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("FATURA CONSOLIDADA DO MÊS", style = MaterialTheme.typography.labelSmall, color = GlassPalette.TextSecondary)
                                Text(ReportEngine.formatCurrency(remainingInvoice), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = GlassPalette.TextPrimary)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Bruto: ${ReportEngine.formatCurrency(state.totalMonthInvoice)}", style = MaterialTheme.typography.bodySmall, color = GlassPalette.TextSecondary)
                                    Text("Abatido: ${ReportEngine.formatCurrency(state.totalMonthPaid)}", style = MaterialTheme.typography.bodySmall, color = GlassPalette.EmeraldAccent)
                                }
                                LiquidGlassBar(progress = paidRatio, accentColor = GlassPalette.EmeraldAccent)
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { showHolderDialog = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.PersonAdd, null, tint = GlassPalette.CyanGlow)
                                Spacer(Modifier.width(6.dp))
                                Text("Pessoa", color = GlassPalette.TextPrimary)
                            }
                            OutlinedButton(onClick = { showCardDialog = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.AddCard, null, tint = GlassPalette.IndigoGlow)
                                Spacer(Modifier.width(6.dp))
                                Text("Cartão", color = GlassPalette.TextPrimary)
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                            items(state.holderSummaries, key = { it.holder.id }) { item ->
                                val isExpanded = expandedHolderId == item.holder.id
                                val usageRatio = if (item.holder.subLimit > 0) (item.globalCommitted / item.holder.subLimit).toFloat() else 0f
                                val barColor = when {
                                    usageRatio >= 1f -> GlassPalette.CoralDanger
                                    usageRatio >= 0.8f -> GlassPalette.AmberWarn
                                    else -> GlassPalette.CyanGlow
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .liquidGlassSurface(cornerRadius = 20.dp, accentTint = barColor)
                                        .clickable { expandedHolderId = if (isExpanded) null else item.holder.id }
                                        .padding(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column {
                                                Text(item.holder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GlassPalette.TextPrimary)
                                                Text("A Pagar no Mês: ${ReportEngine.formatCurrency(item.monthlyNetDue)}", style = MaterialTheme.typography.bodyMedium, color = barColor, fontWeight = FontWeight.SemiBold)
                                            }
                                            Row {
                                                IconButton(onClick = {
                                                    ReportEngine.sharePdfAndTextViaWhatsApp(
                                                        context = context,
                                                        holder = item.holder,
                                                        month = state.selectedMonth,
                                                        year = state.selectedYear,
                                                        installments = item.monthlyInstallments,
                                                        paidInMonth = item.monthlyPaid,
                                                        globalCommitted = item.globalCommitted
                                                    )
                                                }) { Icon(Icons.Default.Share, contentDescription = "Exportar PDF/WhatsApp", tint = GlassPalette.CyanGlow) }
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    tint = GlassPalette.TextSecondary,
                                                    modifier = Modifier.align(Alignment.CenterVertically)
                                                )
                                            }
                                        }

                                        if (item.holder.subLimit > 0) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Sublimite Livre: ${ReportEngine.formatCurrency(item.availableSubLimit)}", style = MaterialTheme.typography.labelSmall, color = GlassPalette.TextSecondary)
                                                Text("Teto: ${ReportEngine.formatCurrency(item.holder.subLimit)}", style = MaterialTheme.typography.labelSmall, color = GlassPalette.TextSecondary)
                                            }
                                            LiquidGlassBar(progress = usageRatio, accentColor = barColor)
                                        }

                                        AnimatedVisibility(visible = isExpanded) {
                                            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                                if (item.monthlyInstallments.isEmpty()) {
                                                    Text("Nenhuma parcela neste mês.", style = MaterialTheme.typography.bodySmall, color = GlassPalette.TextSecondary)
                                                } else {
                                                    item.monthlyInstallments.forEach { inst ->
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text("• ${inst.merchantSnapshot} (${inst.installmentNumber}/${inst.totalInstallments})", style = MaterialTheme.typography.bodySmall, color = GlassPalette.TextPrimary)
                                                            Text(ReportEngine.formatCurrency(inst.amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = GlassPalette.CyanGlow)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                            items(state.cardSummaries, key = { it.card.id }) { item ->
                                val ratio = if (item.card.totalLimit > 0) (item.globalCommitted / item.card.totalLimit).toFloat() else 0f
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .liquidGlassSurface(cornerRadius = 22.dp, accentTint = GlassPalette.IndigoGlow)
                                        .padding(18.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(item.card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GlassPalette.TextPrimary)
                                            Text("•••• ${item.card.lastFourDigits}", style = MaterialTheme.typography.titleSmall, color = GlassPalette.CyanGlow)
                                        }
                                        Text("Fecha dia ${item.card.closingDay} • Vence dia ${item.card.dueDay}", style = MaterialTheme.typography.labelMedium, color = GlassPalette.TextSecondary)
                                        Text("Disponível Real: ${ReportEngine.formatCurrency(item.availableLimit)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = GlassPalette.EmeraldAccent)
                                        LiquidGlassBar(progress = ratio, accentColor = GlassPalette.IndigoGlow)
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        var email by remember { mutableStateOf(viewModel.credentialStore.getSenderEmail()) }
                        var pass by remember { mutableStateOf(viewModel.credentialStore.getAppPassword()) }

                        Box(modifier = Modifier.fillMaxWidth().liquidGlassSurface(cornerRadius = 22.dp).padding(18.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Captura em Tempo Real & Cofre Keystore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GlassPalette.TextPrimary)
                                Button(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = GlassPalette.IndigoGlow)) {
                                    Icon(Icons.Default.NotificationsActive, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Conceder Permissão às Notificações")
                                }
                                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Gmail Remetente (SMTP TLS)") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Senha de App Google (16 caracteres)") }, modifier = Modifier.fillMaxWidth())
                                Button(
                                    onClick = { viewModel.credentialStore.saveSmtpCredentials(email, pass) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = GlassPalette.CyanGlow)
                                ) {
                                    Icon(Icons.Default.Lock, null, tint = GlassPalette.ObsidianBg)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Criptografar no Android Keystore", color = GlassPalette.ObsidianBg, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExpenseDialog && state.cards.isNotEmpty() && state.holders.isNotEmpty()) {
        var merchant by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var parcels by remember { mutableStateOf("1") }
        var selectedCardId by remember { mutableLongStateOf(state.cards.first().id) }
        var selectedHolderId by remember { mutableLongStateOf(state.holders.first().id) }

        AlertDialog(
            onDismissRequest = { showExpenseDialog = false },
            title = { Text("Lançar Compra") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cartão Utilizado:", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        LiquidPillSelector(
                            options = state.cards.map { it.id to "${it.name} (${it.lastFourDigits})" },
                            selectedId = selectedCardId,
                            onSelect = { cardId ->
                                selectedCardId = cardId
                                state.cards.find { it.id == cardId }?.defaultHolderId?.let { selectedHolderId = it }
                            }
                        )
                    }
                    Text("Responsável pelo Gasto:", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        LiquidPillSelector(
                            options = state.holders.map { it.id to it.name },
                            selectedId = selectedHolderId,
                            onSelect = { selectedHolderId = it }
                        )
                    }
                    OutlinedTextField(merchant, { merchant = it }, label = { Text("Estabelecimento") })
                    OutlinedTextField(amount, { amount = it }, label = { Text("Valor Total (R$)") })
                    OutlinedTextField(parcels, { parcels = it }, label = { Text("Parcelas (1 a 72)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = amount.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (merchant.isNotBlank() && v > 0) {
                        viewModel.addExpense(selectedCardId, selectedHolderId, merchant, v, (parcels.toIntOrNull() ?: 1).coerceIn(1, 72))
                        showExpenseDialog = false
                    }
                }) { Text("Confirmar") }
            }
        )
    }

    if (showPaymentDialog && state.holders.isNotEmpty()) {
        var amount by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var selectedHolderId by remember { mutableLongStateOf(state.holders.first().id) }
        var selectedCardId by remember { mutableStateOf(state.cards.firstOrNull()?.id) }

        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("Registrar Abatimento / Pix") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quem Pagou:", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        LiquidPillSelector(options = state.holders.map { it.id to it.name }, selectedId = selectedHolderId, onSelect = { selectedHolderId = it })
                    }
                    if (state.cards.isNotEmpty()) {
                        Text("Liberar Limite do Cartão:", style = MaterialTheme.typography.labelMedium)
                        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            LiquidPillSelector(options = state.cards.map { it.id to it.name }, selectedId = selectedCardId, onSelect = { selectedCardId = it })
                        }
                    }
                    OutlinedTextField(amount, { amount = it }, label = { Text("Valor Recebido (R$)") })
                    OutlinedTextField(notes, { notes = it }, label = { Text("Observação") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = amount.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (v > 0) {
                        viewModel.addPayment(selectedHolderId, selectedCardId, v, notes)
                        showPaymentDialog = false
                    }
                }) { Text("Abater") }
            }
        )
    }

    if (showHolderDialog) {
        var name by remember { mutableStateOf("") }
        var whats by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var subLimit by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showHolderDialog = false },
            title = { Text("Novo Titular / Dependente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nome") })
                    OutlinedTextField(whats, { whats = it }, label = { Text("WhatsApp (5511999999999)") })
                    OutlinedTextField(email, { email = it }, label = { Text("E-mail para Alertas") })
                    OutlinedTextField(subLimit, { subLimit = it }, label = { Text("Sublimite (R$)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.addHolder(name, whats, email, subLimit.replace(",", ".").toDoubleOrNull() ?: 0.0)
                        showHolderDialog = false
                    }
                }) { Text("Salvar") }
            }
        )
    }

    if (showCardDialog) {
        var name by remember { mutableStateOf("") }
        var lastFour by remember { mutableStateOf("") }
        var limit by remember { mutableStateOf("") }
        var closing by remember { mutableStateOf("10") }
        var due by remember { mutableStateOf("17") }
        var defaultHolderId by remember { mutableStateOf(state.holders.firstOrNull()?.id) }

        AlertDialog(
            onDismissRequest = { showCardDialog = false },
            title = { Text("Novo Cartão") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.holders.isNotEmpty()) {
                        Text("Titular Padrão (Automação Push):", style = MaterialTheme.typography.labelMedium)
                        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            LiquidPillSelector(options = state.holders.map { it.id to it.name }, selectedId = defaultHolderId, onSelect = { defaultHolderId = it })
                        }
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("Apelido (Ex: Nubank Adicional)") })
                    OutlinedTextField(lastFour, { lastFour = it }, label = { Text("Últimos 4 Dígitos") })
                    OutlinedTextField(limit, { limit = it }, label = { Text("Limite Total (R$)") })
                    OutlinedTextField(closing, { closing = it }, label = { Text("Dia Fechamento (1-28)") })
                    OutlinedTextField(due, { due = it }, label = { Text("Dia Vencimento (1-28)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank() && lastFour.isNotBlank()) {
                        viewModel.addCard(
                            name = name,
                            lastFour = lastFour,
                            limit = limit.replace(",", ".").toDoubleOrNull() ?: 0.0,
                            closing = closing.toIntOrNull() ?: 10,
                            due = due.toIntOrNull() ?: 17,
                            defaultHolderId = defaultHolderId
                        )
                        showCardDialog = false
                    }
                }) { Text("Salvar") }
            }
        )
    }
}
""",
    "app/src/main/java/com/cardcontrol/app/MainActivity.kt": """package com.cardcontrol.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardcontrol.app.ui.MainScreen
import com.cardcontrol.app.ui.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val vm: MainViewModel = viewModel()
                MainScreen(
                    viewModel = vm,
                    onOpenNotificationSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }
        }
    }
}
""",
    "app/src/test/java/com/cardcontrol/app/SecurityAndDomainTestHarness.kt": """package com.cardcontrol.app

import com.cardcontrol.app.data.repository.CardRepository
import com.cardcontrol.app.security.SecuritySanitizer
import com.cardcontrol.app.service.BankNotificationParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SecurityAndDomainTestHarness {

    @Test
    fun `harness deve bloquear tentativa de Path Traversal no nome do titular`() {
        val maliciousName = "../../etc/passwd\\u0000; rm -rf"
        val sanitized = SecuritySanitizer.sanitizeFileNameSegment(maliciousName)
        assertFalse(sanitized.contains(".."))
        assertFalse(sanitized.contains("/"))
        assertEquals("etcpasswd_rm_-rf", sanitized)
    }

    @Test
    fun `harness deve neutralizar injecao de cabecalho SMTP CRLF`() {
        val maliciousMerchant = "LOJA X\\r\\nBcc: atacante@dominio.com\\r\\nSubject: Hacked"
        val sanitized = SecuritySanitizer.sanitizeHeaderField(maliciousMerchant)
        assertFalse(sanitized.contains("\\r"))
        assertFalse(sanitized.contains("\\n"))
        assertTrue(sanitized.startsWith("LOJA X Bcc:"))
    }

    @Test
    fun `harness deve ignorar notificacoes de compra negada ou estorno`() {
        val denied = BankNotificationParser.parse(
            packageName = "com.nu.production",
            title = "Compra negada",
            text = "Compra aprovada anteriormente foi estornada no valor de R$ 350,00 no cartao final 4321",
            timestamp = 1700000000000L
        )
        assertNull(denied)
    }

    @Test
    fun `harness deve rejeitar pacotes nao bancarios mesmo com texto identico`() {
        val spoofed = BankNotificationParser.parse(
            packageName = "com.whatsapp",
            title = "Compra aprovada",
            text = "Compra aprovada de R$ 500,00 em MERCADO LIVRE no cartao final 4321",
            timestamp = 1700000000000L
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
        val parcels = CardRepository.splitInstallmentAmounts(1000.00, 7)
        assertEquals(7, parcels.size)
        assertEquals(142.90, parcels.first(), 0.001)
        assertEquals(142.85, parcels.last(), 0.001)
        assertEquals(1000.00, parcels.sum(), 0.0001)
    }

    @Test
    fun `harness deve avancar fatura na virada de ano quando compra ocorre apos fechamento de dezembro`() {
        val decPurchaseAfterClosing = LocalDate.of(2026, 12, 15)
        val firstParcelDate = CardRepository.calculateFirstInstallmentMonth(decPurchaseAfterClosing, closingDay = 10)
        assertEquals(2027, firstParcelDate.year)
        assertEquals(1, firstParcelDate.monthValue)
    }
}
"""
}

def main():
    # Corrige escape duplo de Regex gerado pelas strings multi-linha do Python
    regex_files = [
        "app/src/main/java/com/cardcontrol/app/security/SecuritySanitizer.kt",
        "app/src/main/java/com/cardcontrol/app/service/BankNotificationParser.kt"
    ]
    for rf in regex_files:
        FILES[rf] = FILES[rf].replace("\\\\", "\\")

    FILES["gradle/wrapper/gradle-wrapper.properties"] = (
        "distributionBase=GRADLE_USER_HOME\n"
        "distributionPath=wrapper/dists\n"
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.7-bin.zip\n"
        "networkTimeout=10000\n"
        "validateDistributionUrl=true\n"
        "zipStoreBase=GRADLE_USER_HOME\n"
        "zipStorePath=wrapper/dists\n"
    )

    for rel_path, content in FILES.items():
        full_path = os.path.join(PROJECT_ROOT, rel_path)
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        with open(full_path, "w", encoding="utf-8") as f:
            f.write(content.strip() + "\n")

    with zipfile.ZipFile(ZIP_NAME, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, _, files in os.walk(PROJECT_ROOT):
            for file in files:
                full_path = os.path.join(root, file)
                zf.write(full_path, os.path.relpath(full_path, PROJECT_ROOT))

    print(f"[OK] Workspace '{PROJECT_ROOT}/' pronto para compilacao.")


if __name__ == "__main__":
    main()
