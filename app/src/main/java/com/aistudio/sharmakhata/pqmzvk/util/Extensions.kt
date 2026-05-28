package com.aistudio.sharmakhata.pqmzvk.util

/**
 * Format a Double as Indian Rupees (e.g. "₹1,234.56").
 */
fun Double.toRupees(): String = FormatUtils.formatCurrency(this)

/**
 * Parse an ISO date string and format as "dd MMM yyyy".
 * Falls back to the raw string if parsing fails.
 */
fun String.toFormattedDate(): String = FormatUtils.formatDate(this)

/**
 * Return the string or a dash "—" if null/blank.
 */
fun String?.orDash(): String = if (isNullOrBlank()) "—" else this
