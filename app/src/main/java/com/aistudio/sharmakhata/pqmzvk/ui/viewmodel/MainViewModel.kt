package com.aistudio.sharmakhata.pqmzvk.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.sharmakhata.pqmzvk.data.exception.NetworkException
import com.aistudio.sharmakhata.pqmzvk.data.model.DailyReport
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import com.aistudio.sharmakhata.pqmzvk.data.remote.AddCustomerRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.AddPaymentRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.ApiClient
import com.aistudio.sharmakhata.pqmzvk.data.remote.BillItemRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.CreateBillRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.SendInvoiceRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.SendReminderRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.SendStatementRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.MarkBillPaidRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.RequestLoginCodeRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.VerifyLoginCodeRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.RegisterStoreRequest
import com.aistudio.sharmakhata.pqmzvk.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.LiveSyncManager

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

sealed class OperationState {
    object Idle : OperationState()
    object Loading : OperationState()
    data class Success(val message: String) : OperationState()
    data class Error(val message: String) : OperationState()
}

class MainViewModel : ViewModel() {

    private val _dbState = MutableStateFlow<UiState<FullDatabase>>(UiState.Loading)
    val dbState: StateFlow<UiState<FullDatabase>> = _dbState.asStateFlow()

    private val _reportState = MutableStateFlow<UiState<DailyReport>>(UiState.Loading)
    val reportState: StateFlow<UiState<DailyReport>> = _reportState.asStateFlow()

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()

    private val _lastCreatedBillId = MutableStateFlow<String?>(null)
    val lastCreatedBillId: StateFlow<String?> = _lastCreatedBillId.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _registeredStoreId = MutableStateFlow<String?>(null)
    val registeredStoreId: StateFlow<String?> = _registeredStoreId.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _logoutEvent = MutableStateFlow(false)
    val logoutEvent: StateFlow<Boolean> = _logoutEvent.asStateFlow()

    init {
        viewModelScope.launch {
            ApiClient.unauthorizedEvent.collect {
                _logoutEvent.value = true
            }
        }
    }

    fun consumeLogoutEvent() {
        _logoutEvent.value = false
    }

    // Don't fetch data automatically on init to prevent crashes
    // init { fetchData() }

    fun fetchData(context: Context? = null) {
        println("MainViewModel: fetchData() called - starting sync")
        _dbState.value = UiState.Loading
        _reportState.value = UiState.Loading
        _syncError.value = null

        // Check network before starting sync
        if (context != null && !NetworkUtils.isNetworkAvailable(context)) {
            val errorMsg = "No internet connection. Please check your network and try again."
            _syncError.value = errorMsg
            _dbState.value = UiState.Error(errorMsg)
            _reportState.value = UiState.Error(errorMsg)
            return
        }

        // Actually start the LiveSyncManager
        println("MainViewModel: Starting LiveSyncManager")
        LiveSyncManager.start()

        // Initialize LiveSyncManager (already done in init)
        viewModelScope.launch {
            println("MainViewModel: Starting to collect fullDatabase from LiveSyncManager")
            // Collect live full database updates
            LiveSyncManager.fullDatabase.collect { db ->
                println("MainViewModel: Received fullDatabase update: ${db != null}")
                if (db != null) {
                    println("MainViewModel: Database received successfully, customers: ${db.customers.size}, bills: ${db.bills.size}")
                    _dbState.value = UiState.Success(db)
                }
            }
        }
        viewModelScope.launch {
            println("MainViewModel: Starting to collect dailyReport from LiveSyncManager")
            // Collect live daily report updates
            LiveSyncManager.dailyReport.collect { report ->
                println("MainViewModel: Received dailyReport update: ${report != null}")
                if (report != null) {
                    println("MainViewModel: Report received successfully")
                    _reportState.value = UiState.Success(report)
                }
            }
        }
        viewModelScope.launch {
            println("MainViewModel: Starting to collect syncError from LiveSyncManager")
            // Collect sync errors
            LiveSyncManager.syncError.collect { error ->
                println("MainViewModel: Received syncError: $error")
                if (error != null) {
                    println("MainViewModel: Sync error detected, updating UI states")
                    _syncError.value = error
                    _dbState.value = UiState.Error(error)
                    _reportState.value = UiState.Error(error)
                }
            }
        }
    }

    // Start live sync after successful authentication
    fun startLiveSyncIfNeeded() {
        if (_authToken.value != null) {
            LiveSyncManager.start()
        }
    }

    fun setSyncInterval(millis: Long) {
        LiveSyncManager.intervalMillis = millis
    }

    fun addCustomer(context: Context, name: String, phone: String) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                // Check network before API call
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _operationState.value = OperationState.Error("No internet connection. Please check your network and try again.")
                    return@launch
                }
                
                val response = ApiClient.apiService.addCustomer(AddCustomerRequest(name, phone))
                if (response.isSuccessful && response.body()?.success == true) {
                    _operationState.value = OperationState.Success("Customer added successfully")
                    fetchData(context) // Refresh data
                } else {
                    _operationState.value = OperationState.Error(response.body()?.message ?: "Failed to add customer")
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Failed to add customer: ${e.message}")
            }
        }
    }

    fun addPayment(context: Context, customerId: String, amount: Double, note: String?) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _operationState.value = OperationState.Error("No internet connection. Please check your network and try again.")
                    return@launch
                }
                val response = ApiClient.apiService.addPayment(AddPaymentRequest(customerId, amount, note))
                if (response.isSuccessful) {
                    _operationState.value = OperationState.Success("Payment recorded successfully")
                    fetchData(context) // Refresh data
                } else {
                    _operationState.value = OperationState.Error("Failed to record payment")
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Failed to record payment: ${e.message}")
            }
        }
    }

    fun createBill(context: Context, customerId: String, amount: Double, items: List<BillItemRequest>?) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _operationState.value = OperationState.Error("No internet connection. Please check your network and try again.")
                    return@launch
                }
                val response = ApiClient.apiService.createBill(CreateBillRequest(customerId, amount, items))
                val billId = response.body()?.billId
                if (response.isSuccessful && !billId.isNullOrBlank()) {
                    _lastCreatedBillId.value = billId
                    _operationState.value = OperationState.Success("Bill created (ID: $billId)")
                    fetchData(context) // Refresh data
                } else {
                    _operationState.value = OperationState.Error("Failed to create bill")
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Failed to create bill: ${e.message}")
            }
        }
    }

    fun resetOperationState() {
        _operationState.value = OperationState.Idle
        _lastCreatedBillId.value = null
    }

    fun sendInvoiceOnWhatsApp(context: Context, billId: String) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _operationState.value = OperationState.Error("No internet connection. Please check your network and try again.")
                    return@launch
                }
                val response = ApiClient.apiService.sendInvoice(SendInvoiceRequest(billId))
                val ok = response.isSuccessful && response.body()?.success == true
                _operationState.value =
                    if (ok) OperationState.Success("Invoice sent on WhatsApp")
                    else OperationState.Error("Failed to send invoice")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Failed to send invoice: ${e.message}")
            }
        }
    }

    fun sendStatementOnWhatsApp(context: Context, customerId: String) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _operationState.value = OperationState.Error("No internet connection. Please check your network and try again.")
                    return@launch
                }
                val response = ApiClient.apiService.sendStatement(SendStatementRequest(customerId))
                val ok = response.isSuccessful && response.body()?.success == true
                _operationState.value =
                    if (ok) OperationState.Success("Statement sent on WhatsApp")
                    else OperationState.Error("Failed to send statement")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Failed to send statement: ${e.message}")
            }
        }
    }

    fun sendReminderOnWhatsApp(context: Context, customerId: String, message: String? = null) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _operationState.value = OperationState.Error("No internet connection. Please check your network and try again.")
                    return@launch
                }
                val response = ApiClient.apiService.sendReminder(SendReminderRequest(customerId, message))
                val ok = response.isSuccessful && response.body()?.success == true
                _operationState.value =
                    if (ok) OperationState.Success("Reminder sent on WhatsApp")
                    else OperationState.Error("Failed to send reminder")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Failed to send reminder: ${e.message}")
            }
        }
    }

    fun markBillPaid(context: Context, billId: String) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _operationState.value = OperationState.Error("No internet connection. Please check your network and try again.")
                    return@launch
                }
                val response = ApiClient.apiService.markBillPaid(MarkBillPaidRequest(billId))
                val ok = response.isSuccessful && response.body()?.success == true
                _operationState.value =
                    if (ok) OperationState.Success("Bill marked as paid")
                    else OperationState.Error("Failed to mark bill paid")
                if (ok) fetchData(context)
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Failed to mark bill paid: ${e.message}")
            }
        }
    }

    fun requestLoginCode(storeId: String, phone: String) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.requestLoginCode(RequestLoginCodeRequest(storeId, phone))
                val ok = response.isSuccessful && response.body()?.success == true
                _operationState.value =
                    if (ok) OperationState.Success("Code sent on WhatsApp")
                    else OperationState.Error("Failed to request code")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Failed to request code: ${e.message}")
            }
        }
    }

    fun verifyLoginCode(storeId: String, phone: String, code: String, context: android.content.Context) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.verifyLoginCode(VerifyLoginCodeRequest(storeId, phone, code))
                val token = response.body()?.token
                val ok = response.isSuccessful && !token.isNullOrBlank()
                if (ok) {
                    _authToken.value = token
                    // Save token to SessionManager immediately before starting sync
                    com.aistudio.sharmakhata.pqmzvk.util.SessionManager.setToken(context, token)
                    // Reset LiveSyncManager to ensure fresh start
                    LiveSyncManager.stop()
                    // Start live sync now that we have a token
                    LiveSyncManager.start()
                    _operationState.value = OperationState.Success("Logged in")
                } else {
                    _operationState.value = OperationState.Error("Invalid code")
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Login failed: ${e.message}")
            }
        }
    }

    fun consumeAuthToken(): String? {
        val t = _authToken.value
        _authToken.value = null
        return t
    }

    fun consumeRegisteredStoreId(): String? {
        val id = _registeredStoreId.value
        _registeredStoreId.value = null
        return id
    }

    fun registerStore(
        storeName: String,
        ownerName: String,
        phone: String,
        email: String,
        address: String?,
    ) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.registerStore(
                    RegisterStoreRequest(
                        store_name = storeName,
                        owner_name = ownerName,
                        phone = phone,
                        email = email,
                        address = address,
                    )
                )
                val storeId = response.body()?.store_id
                val ok = response.isSuccessful && !storeId.isNullOrBlank()
                if (ok) {
                    _registeredStoreId.value = storeId
                    _operationState.value = OperationState.Success("Store registered (ID: $storeId)")
                } else {
                    _operationState.value = OperationState.Error(response.body()?.message ?: "Failed to register store")
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Failed to register store: ${e.message}")
            }
        }
    }
}
