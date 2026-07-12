package com.sbi.surakshasathi.feature.messagescan.data.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sbi.surakshasathi.feature.messagescan.data.ingestion.NotificationOnlySmsStrategy
import com.sbi.surakshasathi.feature.messagescan.data.mapper.IncomingMessageMapper
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageSource
import com.sbi.surakshasathi.feature.messagescan.domain.usecase.ProcessIncomingMessageUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Notification Listener Service for WhatsApp, Telegram, and SMS messages.
 *
 * Flow:
 * 1. System delivers [onNotificationPosted] for every new notification.
 * 2. We filter to [allowedPackages] only (scoped, not QUERY_ALL_PACKAGES).
 * 3. Extract sender (title) and body (text) from notification extras.
 * 4. Determine [MessageSource] from package name.
 * 5. Classify, persist, and (if flagged) escalate to RAG via
 *    [ProcessIncomingMessageUseCase] on IO dispatcher.
 * 6. For SMS notifications, also forward to [NotificationOnlySmsStrategy]
 *    so the SMS strategy Flow emits (used by any Flow-based consumers).
 *
 * Privacy: message text is processed for classification only.
 * No body is persisted in full without user consent (body purged after TTL).
 *
 * Performance: classification is < 50 ms; service lifecycle ends when
 * notification listener access is revoked (system manages lifecycle).
 */
@AndroidEntryPoint
class MessageNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var processIncomingMessageUseCase: ProcessIncomingMessageUseCase

    @Inject lateinit var mapper: IncomingMessageMapper

    @Inject lateinit var notificationOnlySmsStrategy: NotificationOnlySmsStrategy

    // SupervisorJob so one failing classification doesn't cancel others
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val allowedPackages =
        mapOf(
            "com.whatsapp" to MessageSource.WHATSAPP,
            "com.whatsapp.w4b" to MessageSource.WHATSAPP,
            "org.telegram.messenger" to MessageSource.TELEGRAM,
            "org.telegram.messenger.web" to MessageSource.TELEGRAM,
            "org.telegram.plus" to MessageSource.TELEGRAM,
            "com.google.android.apps.messaging" to MessageSource.SMS,
            "com.samsung.android.messaging" to MessageSource.SMS,
            "com.android.mms" to MessageSource.SMS,
            "com.oneplus.mms" to MessageSource.SMS,
        )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val source = allowedPackages[notification.packageName] ?: return

        // Only process messaging notifications (not group summaries, etc.)
        if (notification.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.notification?.extras ?: return
        val sender =
            extras.getCharSequence("android.title")?.toString()?.trim()
                ?: return
        val body =
            (
                extras.getCharSequence("android.bigText")?.toString()
                    ?: extras.getCharSequence("android.text")?.toString()
            )?.trim() ?: return

        if (body.length < 3) return // Ignore trivially short notifications

        serviceScope.launch {
            val raw =
                mapper.map(
                    body = body,
                    sender = sender,
                    source = source,
                )
            // Classify, persist, and (if flagged) escalate to RAG + notify
            processIncomingMessageUseCase(raw)

            // If this is an SMS notification, also notify the SMS strategy flow
            if (source == MessageSource.SMS) {
                notificationOnlySmsStrategy.onSmsNotificationReceived(body, sender)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Not needed for classification
    }

    override fun onDestroy() {
        super.onDestroy()
        // CoroutineScope is tied to SupervisorJob — it will be GC'd
        // No explicit cancel needed since serviceScope is not app-scoped
    }
}
