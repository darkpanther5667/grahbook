package com.aistudio.sharmakhata.pqmzvk.data.remote

import com.aistudio.sharmakhata.pqmzvk.data.model.Customer
import com.aistudio.sharmakhata.pqmzvk.data.model.DailyReport
import com.aistudio.sharmakhata.pqmzvk.data.model.DeltaChanges
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/auth/request-code")
    suspend fun requestLoginCode(@Body request: RequestLoginCodeRequest): retrofit2.Response<BasicSuccessResponse>

    @POST("api/auth/verify-code")
    suspend fun verifyLoginCode(@Body request: VerifyLoginCodeRequest): retrofit2.Response<VerifyLoginResponse>

    @POST("api/auth/login")
    suspend fun loginWithPassword(@Body request: LoginWithPasswordRequest): retrofit2.Response<VerifyLoginResponse>

    @POST("api/register-store")
    suspend fun registerStore(@Body request: RegisterStoreRequest): retrofit2.Response<RegisterStoreResponse>

    @POST("api/store/update")
    suspend fun updateStoreProfile(@Body request: UpdateStoreProfileRequest): retrofit2.Response<BasicSuccessResponse>

    @GET("api/db")
    suspend fun getFullDatabase(): retrofit2.Response<FullDatabase>

    /** Delta sync — only fetch records changed since the given ISO 8601 timestamp.
     *  Falls back to full database if server doesn't support delta endpoint. */
    @GET("api/db/changes")
    suspend fun getDeltaChanges(@Query("since") since: String): retrofit2.Response<DeltaChanges>

    @GET("api/report")
    suspend fun getDailyReport(): retrofit2.Response<DailyReport>

    @POST("api/db")
    suspend fun updateDatabase(@Body db: FullDatabase): retrofit2.Response<Unit>

    @POST("api/customer/add")
    suspend fun addCustomer(@Body request: AddCustomerRequest): retrofit2.Response<AddCustomerResponse>

    @POST("api/payment/add")
    suspend fun addPayment(@Body request: AddPaymentRequest): retrofit2.Response<Unit>

    @POST("api/bill/create")
    suspend fun createBill(@Body request: CreateBillRequest): retrofit2.Response<CreateBillResponse>

    @POST("api/whatsapp/send-invoice")
    suspend fun sendInvoice(@Body request: SendInvoiceRequest): retrofit2.Response<BasicSuccessResponse>

    @POST("api/whatsapp/send-statement")
    suspend fun sendStatement(@Body request: SendStatementRequest): retrofit2.Response<BasicSuccessResponse>

    @POST("api/whatsapp/send-reminder")
    suspend fun sendReminder(@Body request: SendReminderRequest): retrofit2.Response<BasicSuccessResponse>

    @POST("api/bill/mark-paid")
    suspend fun markBillPaid(@Body request: MarkBillPaidRequest): retrofit2.Response<BasicSuccessResponse>

    @GET("api/items")
    suspend fun getStoredItems(): StoredItemsResponse

    @DELETE("api/transaction/{id}")
    suspend fun deleteTransaction(@Path("id") id: String): retrofit2.Response<BasicSuccessResponse>

    @DELETE("api/bill/{id}")
    suspend fun deleteBill(@Path("id") id: String): retrofit2.Response<BasicSuccessResponse>
}

data class AddCustomerRequest(
    val name: String,
    val phone: String
)

data class AddCustomerResponse(
    val success: Boolean,
    val customer: Customer?,
    val message: String?
)

data class AddPaymentRequest(
    val customerId: String,
    val amount: Double,
    val note: String?,
    @Json(name = "payment_mode") val paymentMode: String = "cash",
    val type: String = "payment"
)

data class CreateBillRequest(
    val customerId: String,
    val amount: Double,
    val items: List<BillItemRequest>?,
    @Json(name = "gst_type") val gstType: String = "cgst_sgst",
    @Json(name = "gst_rate") val gstRate: Int = 0,
    @Json(name = "taxable_amount") val taxableAmount: Double = 0.0,
    @Json(name = "total_cgst") val totalCgst: Double = 0.0,
    @Json(name = "total_sgst") val totalSgst: Double = 0.0,
    @Json(name = "total_igst") val totalIgst: Double = 0.0,
    @Json(name = "grand_total") val grandTotal: Double = 0.0
)

data class CreateBillResponse(
    val success: Boolean,
    val customerName: String? = null,
    val billId: String? = null,
    val amount: Double? = null,
    val netOutstanding: Double? = null,
    val message: String? = null
)

data class BillItemRequest(
    val name: String,
    val price: Double,
    val qty: Int,
    @Json(name = "hsn_code") val hsnCode: String = "",
    @Json(name = "gst_rate") val gstRate: Int = 0
)

data class SendInvoiceRequest(
    val billId: String
)

data class SendStatementRequest(
    val customerId: String
)

data class SendReminderRequest(
    val customerId: String,
    val message: String? = null
)

data class BasicSuccessResponse(
    val success: Boolean,
    val message: String? = null
)

data class MarkBillPaidRequest(
    val billId: String
)

data class RequestLoginCodeRequest(
    val storeId: String,
    val phone: String
)

data class VerifyLoginCodeRequest(
    val storeId: String,
    val phone: String,
    val code: String
)

data class VerifyLoginResponse(
    val success: Boolean,
    val token: String? = null,
    val store: Any? = null,
    val message: String? = null
)

data class LoginWithPasswordRequest(
    val phone: String,
    val password: String
)

data class RegisterStoreRequest(
    val store_name: String,
    val owner_name: String,
    val phone: String,
    val email: String,
    val business_type: String? = "retail",
    val plan: String? = "basic",
    val address: String? = null,
    val gstin: String? = null,
    val password: String? = null,
)

data class RegisterStoreResponse(
    val status: String,
    val store_id: String? = null,
    val message: String? = null
)

data class StoredItemsResponse(
    val success: Boolean,
    val items: List<StoredItem> = emptyList(),
    val message: String? = null
)

data class StoredItem(
    val name: String,
    val price: Double,
    val count: Int = 0,
    val lastPrice: Double = 0.0
)

data class UpdateStoreProfileRequest(
    val store_name: String?,
    val owner_name: String?,
    val address: String?,
    val upi_id: String?,
    val gstin: String?,
    val invoice_template: String? = null
)
