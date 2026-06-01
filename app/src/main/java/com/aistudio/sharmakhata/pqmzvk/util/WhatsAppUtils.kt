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

    fun buildReminderMessage(
        customerId: String,
        customerName: String,
        outstanding: Double,
        shopName: String,
        upiId: String? = null
    ): String {
        val amount = FormatUtils.formatCurrency(outstanding)
        val baseUrl = Constants.BASE_URL
        val viewUrl = "${baseUrl}view/customer/$customerId/statement"
        val resolvedUpi = if (upiId.isNullOrBlank()) "sharmakhata@upi" else upiId
        val cleanShopName = shopName.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
        val upiLink = "upi://pay?pa=$resolvedUpi&pn=${Uri.encode(cleanShopName)}&am=$outstanding&cu=INR&tn=Reminder"

        return "🙏 *$shopName*\n\n" +
                "Namaste $customerName ji \uD83D\uDE4F\n\n" +
                "Aapka baaki (outstanding): $amount\n" +
                "Kripya jaldi se payment karein.\n\n" +
                "📊 *View Statement & Pay:* $viewUrl\n" +
                "📱 *Direct UPI Payment:* $upiLink\n\n" +
                "Shukriya 🙏"
    }

    fun buildInvoiceMessage(billId: String, amount: Double, shopName: String): String {
        val formatted = FormatUtils.formatCurrency(amount)
        val baseUrl = Constants.BASE_URL
        val viewUrl = "${baseUrl}view/bill/$billId"
        return "🙏 *$shopName*\n\n" +
                "Namaste \uD83D\uDE4F\n\n" +
                "Aapka bill #$billId — Total: $formatted\n" +
                "Kripya payment karein.\n\n" +
                "🧾 *View Bill & Pay:* $viewUrl\n\n" +
                "Shukriya 🙏"
    }
}
