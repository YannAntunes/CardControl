package com.cardcontrol.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.cardcontrol.app.data.local.AppDatabase
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
        if (!BankNotificationParser.isSupportedPackage(pkg)) return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (sbn.isOngoing) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val postTime = sbn.postTime

        serviceScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).appDao()
            val result = BankNotificationProcessor(dao).process(pkg, title, text, postTime)
            if (result !is AutoCaptureResult.Registered) return@launch

            val holder = result.holder
            val card = result.card
            val expense = result.expense
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
                        cardName = "${card.name} (Final ${card.lastFourDigits})",
                        merchant = expense.merchant,
                        amount = expense.amount,
                        installments = expense.installments,
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
