package com.aistudio.sharmakhata.pqmzvk.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object WhatsAppUtils {

    fun sendMessage(context: Context, phone: String, message: String) {
        val cleanPhone = phone.replace(Regex("[^0-9]"), "")
        val number = when {
            cleanPhone.length == 10 -> "91$cleanPhone"
            cleanPhone.startsWith("91") && cleanPhone.length == 12 -> cleanPhone
            else -> cleanPhone
        }
        val url = "https://wa.me/$number?text=${Uri.encode(message)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // WhatsApp not installed — open in browser
        }
    }

    fun buildReminderMessage(customerName: String, outstanding: Double, shopName: String): String {
        val amount = FormatUtils.formatCurrency(outstanding)
        return "Namaste $customerName ji \uD83D\uDE4F\n" +
                "Aapka baaki: $amount\n" +
                "Kripya jaldi se payment karein.\n" +
                "- $shopName"
    }

    fun buildInvoiceMessage(billId: String, amount: Double, shopName: String): String {
        val formatted = FormatUtils.formatCurrency(amount)
        return "Namaste \uD83D\uDE4F\n" +
                "Aapka bill #$billId — Total: $formatted\n" +
                "Kripya payment karein.\n" +
                "- $shopName"
    }
}
