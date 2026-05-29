package com.aistudio.sharmakhata.pqmzvk.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aistudio.sharmakhata.pqmzvk.MainActivity
import com.aistudio.sharmakhata.pqmzvk.R

object NotificationHelper {

    const val CHANNEL_DAILY = "daily_summary"
    const val CHANNEL_REMINDER = "payment_reminders"
    const val CHANNEL_SYNC = "sync_status"

    const val ID_DAILY_SUMMARY = 1001
    const val ID_PAYMENT_REMINDER = 1002
    const val ID_SYNC = 1003

    private fun getNotificationIcon(context: Context): Int {
        val iconId = context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
        return if (iconId != 0) iconId else android.R.drawable.ic_dialog_info
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_DAILY,
                    "Daily Summary",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "End of day business summary" },
                NotificationChannel(
                    CHANNEL_REMINDER,
                    "Payment Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Unpaid bill reminders" },
                NotificationChannel(
                    CHANNEL_SYNC,
                    "Sync Status",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Data sync notifications" }
            )
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }

    fun showDailySummary(
        context: Context,
        totalBills: Int,
        totalCollections: Double,
        totalOutstanding: Double,
        unpaidBills: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fmt = { amount: Double ->
            java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN")).format(amount)
        }

        val body = buildString {
            appendLine("Today's Summary:")
            appendLine("Bills: $totalBills")
            appendLine("Collections: ${fmt(totalCollections)}")
            appendLine("Outstanding: ${fmt(totalOutstanding)}")
            if (unpaidBills > 0) {
                appendLine()
                appendLine("$unpaidBills unpaid bills pending")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(getNotificationIcon(context))
            .setContentTitle("Grahbook - Daily Report")
            .setContentText("Bills: $totalBills | Collections: ${fmt(totalCollections)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ID_DAILY_SUMMARY, notification)
        } catch (_: SecurityException) {
            // Permission not granted — skip
        }
    }

    fun showPaymentReminder(
        context: Context,
        customerName: String,
        amount: Double,
        daysOverdue: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fmt = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN")).format(amount)
        val overdueText = if (daysOverdue > 0) " ($daysOverdue days overdue)" else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(getNotificationIcon(context))
            .setContentTitle("Payment Reminder")
            .setContentText("$customerName owes $fmt$overdueText")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ID_PAYMENT_REMINDER, notification)
        } catch (_: SecurityException) {
            // Permission not granted — skip
        }
    }

    fun showSyncNotification(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(getNotificationIcon(context))
            .setContentTitle("Syncing data")
            .setContentText(message)
            .setOngoing(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ID_SYNC, notification)
        } catch (_: SecurityException) { }
    }

    fun dismissSyncNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_SYNC)
    }
}
