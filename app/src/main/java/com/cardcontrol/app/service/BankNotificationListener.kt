package com.cardcontrol.app.service

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
            val (txId, matchedCard) = repo.processBankNotification(pkg, title, text, sbn.postTime)
            if (txId == -1L || matchedCard == null) return@launch

            val holderId = matchedCard.defaultHolderId ?: return@launch
            val holder = dao.getHolderById(holderId) ?: return@launch

            if (holder.notifyByEmailOnPurchase && holder.email.isNotBlank()) {
                val creds = CredentialStore(applicationContext)
                if (creds.isConfigured()) {
                    val totalSpent = dao.getTotalHistoricalSpentByHolder(holder.id)
                    val totalPaid = dao.getTotalHistoricalPaidByHolder(holder.id)
                    val currentDebt = (totalSpent - totalPaid).coerceAtLeast(0L)
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
