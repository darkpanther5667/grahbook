package com.aistudio.sharmakhata.pqmzvk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aistudio.sharmakhata.pqmzvk.util.NotificationHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            NotificationHelper.createChannels(context)
            ReminderScheduler.scheduleDailySummary(context)
        }
    }
}
