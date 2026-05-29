package com.aistudio.sharmakhata.pqmzvk.data.remote

import com.aistudio.sharmakhata.pqmzvk.data.model.Customer
import com.aistudio.sharmakhata.pqmzvk.data.model.DailyReport
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("api/auth/request-code")
    suspend fun requestLoginCode(@Body request: RequestLoginCodeRequest): retrofit2.Response<BasicSuccessResponse>

    @POST("api/auth/verify-code")
    suspend fun verifyLoginCode(@Body request: VerifyLoginCodeRequest): retrofit2.Response<VerifyLoginResponse>

    @POST("api/register-store")
    suspend fun registerStore(@Body request: RegisterStoreRequest): retrofit2.Response<RegisterStoreResponse>
    @GET("api/db")
    suspend fun getFullDatabase(): FullDatabase

    @GET("api/report")
    suspend fun getDailyReport(): DailyReport

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
    val note: String?
)

data class CreateBillRequest(
    val customerId: String,
    val amount: Double,
    val items: List<BillItemRequest>?
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
    val qty: Int
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

data class RegisterStoreRequest(
    val store_name: String,
    val owner_name: String,
    val phone: String,
    val email: String,
    val business_type: String? = "retail",
    val plan: String? = "basic",
    val address: String? = null,
    val gstin: String? = null,
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
