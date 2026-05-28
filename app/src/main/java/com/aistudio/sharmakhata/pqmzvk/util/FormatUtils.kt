package com.aistudio.sharmakhata.pqmzvk.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

object FormatUtils {

    fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        return format.format(amount)
    }

    fun formatDate(isoString: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = parser.parse(isoString)
            if (date != null) {
                val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                formatter.format(date)
            } else {
                isoString.take(10)
            }
        } catch (e: Exception) {
            isoString.take(10)
        }
    }

    fun formatDateTime(isoString: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = parser.parse(isoString)
            if (date != null) {
                val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                formatter.format(date)
            } else {
                isoString
            }
        } catch (e: Exception) {
            isoString
        }
    }

    fun formatShortDate(isoString: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = parser.parse(isoString)
            if (date != null) {
                val formatter = SimpleDateFormat("dd MMM", Locale.getDefault())
                formatter.format(date)
            } else {
                isoString.take(10)
            }
        } catch (e: Exception) {
            isoString.take(10)
        }
    }
}
