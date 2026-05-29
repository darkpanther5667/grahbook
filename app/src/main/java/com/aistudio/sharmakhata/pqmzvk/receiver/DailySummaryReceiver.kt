package com.aistudio.sharmakhata.pqmzvk.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aistudio.sharmakhata.pqmzvk.data.local.AppDatabase
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import com.aistudio.sharmakhata.pqmzvk.util.NotificationHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DailySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationHelper.createChannels(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cacheDao = AppDatabase.get(context).cacheDao()
                val json = cacheDao.getJson("full_database") ?: return@launch

                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val db = moshi.adapter(FullDatabase::class.java).fromJson(json) ?: return@launch

                val today = java.time.LocalDate.now().toString()
                val billsToday = db.bills.filter { it.createdAt.startsWith(today) }
                val paidBills = billsToday.count { it.status == "paid" }
                val unpaidBills = billsToday.count { it.status != "paid" }

                val paymentsToday = db.transactions.filter {
                    it.type == "payment" && it.timestamp.startsWith(today)
                }
                val collectionsTotal = paymentsToday.sumOf { it.amount }

                val totalOutstanding = db.customers.sumOf { customer ->
                    val billTotal = db.bills
                        .filter { it.customerId == customer.id && it.status != "paid" }
                        .sumOf { it.total }
                    val paymentTotal = db.transactions
                        .filter { it.customerId == customer.id && it.type == "payment" }
                        .sumOf { it.amount }
                    val creditTotal = db.transactions
                        .filter { it.customerId == customer.id && it.type == "credit" }
                        .sumOf { it.amount }
                    (billTotal + creditTotal - paymentTotal).coerceAtLeast(0.0)
                }

                NotificationHelper.showDailySummary(
                    context,
                    totalBills = billsToday.size,
                    totalCollections = collectionsTotal,
                    totalOutstanding = totalOutstanding,
                    unpaidBills = unpaidBills
                )
            } catch (e: Exception) {
                println("DailySummaryReceiver error: ${e.message}")
            }
        }
    }
}

object ReminderScheduler {
    fun scheduleDailySummary(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: run {
            android.util.Log.e("ReminderScheduler", "AlarmManager not available")
            return
        }
        val intent = Intent(context, DailySummaryReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule daily at 8:00 PM
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 20)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
}
