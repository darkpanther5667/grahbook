package com.aistudio.sharmakhata.pqmzvk.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FullDatabase(
    val shop: Shop?,
    val customers: List<Customer> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val staff: List<Staff> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Shop(
    val name: String? = null,
    val owner: String? = null,
    val address: String? = null,
    @Json(name = "upi_id") val upiId: String? = null,
    val gstin: String? = null,
    @Json(name = "invoice_template") val invoiceTemplate: String? = "modern"
)

@JsonClass(generateAdapter = true)
data class Customer(
    val id: String,
    val name: String,
    val phone: String?,
    @Json(name = "created_at") val createdAt: String?
)

@JsonClass(generateAdapter = true)
data class Transaction(
    val id: String,
    @Json(name = "customer_id") val customerId: String,
    val type: String, // 'payment' or 'credit'
    val amount: Double,
    val note: String?,
    @Json(name = "staff_phone") val staffPhone: String?,
    val timestamp: String,
    @Json(name = "payment_mode") val paymentMode: String = "cash"
)

@JsonClass(generateAdapter = true)
data class Bill(
    val id: String,
    @Json(name = "customer_id") val customerId: String,
    val items: List<BillItem> = emptyList(),
    val total: Double,
    val status: String, // 'unpaid' or 'paid'
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "paid_at") val paidAt: String?,
    @Json(name = "gst_type") val gstType: String? = null,
    @Json(name = "gst_rate") val gstRate: Int = 0,
    @Json(name = "taxable_amount") val taxableAmount: Double = 0.0,
    @Json(name = "total_cgst") val totalCgst: Double = 0.0,
    @Json(name = "total_sgst") val totalSgst: Double = 0.0,
    @Json(name = "total_igst") val totalIgst: Double = 0.0,
    @Json(name = "grand_total") val grandTotal: Double = 0.0,
    @Json(name = "invoice_number") val invoiceNumber: String = ""
)

@JsonClass(generateAdapter = true)
data class BillItem(
    val name: String,
    val qty: Int,
    val price: Double,
    @Json(name = "hsn_code") val hsnCode: String = "",
    @Json(name = "gst_rate") val gstRate: Int = 0,
    @Json(name = "taxable") val taxable: Double = 0.0,
    @Json(name = "cgst") val cgst: Double = 0.0,
    @Json(name = "sgst") val sgst: Double = 0.0,
    @Json(name = "igst") val igst: Double = 0.0,
    @Json(name = "total_with_tax") val totalWithTax: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class Staff(
    val id: String,
    val name: String,
    val phone: String
)

@JsonClass(generateAdapter = true)
data class DailyReport(
    val date: String,
    val billsTotal: Double,
    val paymentTotal: Double,
    val billsCount: Int,
    val outstanding: List<OutstandingCustomer> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OutstandingCustomer(
    val name: String,
    val phone: String?,
    val balance: Double
)

/**
 * Response from the delta sync endpoint [com.aistudio.sharmakhata.pqmzvk.data.remote.ApiService.getDeltaChanges].
 * Contains only records that changed since the given timestamp,
 * plus a fallback full database if the server doesn't support delta sync.
 */
@JsonClass(generateAdapter = true)
data class DeltaChanges(
    @Json(name = "customers") val customers: List<Customer>? = null,
    @Json(name = "deleted_customer_ids") val deletedCustomerIds: List<String>? = null,
    @Json(name = "transactions") val transactions: List<Transaction>? = null,
    @Json(name = "deleted_transaction_ids") val deletedTransactionIds: List<String>? = null,
    @Json(name = "bills") val bills: List<Bill>? = null,
    @Json(name = "deleted_bill_ids") val deletedBillIds: List<String>? = null,
    @Json(name = "server_time") val serverTime: String? = null,
    @Json(name = "fallback_full_db") val fallbackFullDb: FullDatabase? = null
)
