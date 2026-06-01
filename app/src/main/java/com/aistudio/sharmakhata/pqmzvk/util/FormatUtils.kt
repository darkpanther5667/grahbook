package com.aistudio.sharmakhata.pqmzvk.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {

    fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        return format.format(amount)
    }

    fun formatShort(amount: Double): String {
        val abs = kotlin.math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            abs >= 100_000 -> "${sign}₹${String.format("%.1f", abs / 100_000)}L"
            abs >= 1_000   -> "${sign}₹${String.format("%.1f", abs / 1_000)}K"
            else           -> "${sign}₹${abs.toInt()}"
        }
    }

    private fun parseDate(isoString: String): Date? {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            parser.parse(isoString)
        } catch (_: Exception) {
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                parser.parse(isoString)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun formatDate(isoString: String): String {
        return try {
            val date = parseDate(isoString)
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
            val date = parseDate(isoString)
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
            val date = parseDate(isoString)
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

    fun getRelativeTimeSpan(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "N/A"
        val date = parseDate(isoString) ?: return "N/A"
        val now = System.currentTimeMillis()
        val diff = now - date.time

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            diff < 0 -> "Abhi"
            seconds < 60 -> "Abhi"
            minutes < 60 -> "$minutes min pehle"
            hours < 24 -> "$hours ghante pehle"
            days == 1L -> "Kal"
            days < 30 -> "$days din pehle"
            days < 365 -> "${days / 30} mahine pehle"
            else -> "${days / 365} saal pehle"
        }
    }
}
