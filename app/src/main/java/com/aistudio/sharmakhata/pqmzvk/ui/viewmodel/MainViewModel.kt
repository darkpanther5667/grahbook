package com.aistudio.sharmakhata.pqmzvk.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.sharmakhata.pqmzvk.GrahbookApp
import com.aistudio.sharmakhata.pqmzvk.data.exception.NetworkException
import com.aistudio.sharmakhata.pqmzvk.data.local.CacheEntry
import com.aistudio.sharmakhata.pqmzvk.data.local.ExpenseEntity
import com.aistudio.sharmakhata.pqmzvk.data.local.ItemEntity
import com.aistudio.sharmakhata.pqmzvk.data.local.PendingOperation
import com.aistudio.sharmakhata.pqmzvk.data.remote.StoredItem
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
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.util.Calendar

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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = try {
        (application as GrahbookApp).database
    } catch (e: ClassCastException) {
        throw IllegalStateException("MainViewModel requires GrahbookApp as Application class", e)
    }
    private val cacheDao = db.cacheDao()
    private val pendingDao = db.pendingDao()
    private val itemDao = db.itemDao()
    private val expenseDao = db.expenseDao()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val dbAdapter = moshi.adapter(FullDatabase::class.java)
    private val reportAdapter = moshi.adapter(DailyReport::class.java)

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

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _logoutEvent = MutableStateFlow(false)
    val logoutEvent: StateFlow<Boolean> = _logoutEvent.asStateFlow()

    // Items
    private val _items = MutableStateFlow<List<ItemEntity>>(emptyList())
    val items: StateFlow<List<ItemEntity>> = _items.asStateFlow()

    private val _storedItems = MutableStateFlow<List<StoredItem>>(emptyList())
    val storedItems: StateFlow<List<StoredItem>> = _storedItems.asStateFlow()

    private var itemsJob: Job? = null

    // Expenses
    private val _expenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val expenses: StateFlow<List<ExpenseEntity>> = _expenses.asStateFlow()

    private val _todayTotal = MutableStateFlow(0.0)
    val todayTotal: StateFlow<Double> = _todayTotal.asStateFlow()

    private var expensesJob: Job? = null

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

    // ── Cache-first data loading ────────────────────────────────────────────────

    fun fetchData(context: android.content.Context? = null) {
        _syncError.value = null

        // Step 1: Load from cache immediately (instant, no network needed)
        viewModelScope.launch(Dispatchers.IO) {
            loadFromCache()
        }

        // Step 2: Check network — if offline, we're done (cache is shown)
        val isOnline = context == null || NetworkUtils.isNetworkAvailable(context)
        if (!isOnline) {
            _isOffline.value = true
            _syncError.value = "You're offline — showing saved data"
            return
        }
        _isOffline.value = false

        // Step 3: Sync pending offline operations first
        viewModelScope.launch(Dispatchers.IO) {
            syncPendingOperations()
        }

        // Step 4: Ensure LiveSyncManager is running for continuous polling
        LiveSyncManager.start()

        // Collect ongoing LiveSyncManager updates (idempotent)
        viewModelScope.launch {
            LiveSyncManager.fullDatabase.collect { data ->
                if (data != null) {
                    _dbState.value = UiState.Success(data)
                    saveDbToCache(data)
                }
            }
        }
        viewModelScope.launch {
            LiveSyncManager.dailyReport.collect { report ->
                if (report != null) {
                    _reportState.value = UiState.Success(report)
                    saveReportToCache(report)
                }
            }
        }
        viewModelScope.launch {
            LiveSyncManager.syncError.collect { error ->
                if (error != null) {
                    _syncError.value = error
                    // Don't override cached data with error — keep showing cache
                }
            }
        }

        // Step 5: Immediate one-shot fetch — critical after mutations
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val freshDb = ApiClient.apiService.getFullDatabase()
                _dbState.value = UiState.Success(freshDb)
                saveDbToCache(freshDb)
            } catch (e: Exception) {
                println("MainViewModel: Immediate DB fetch failed: ${e.message}")
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val freshReport = ApiClient.apiService.getDailyReport()
                _reportState.value = UiState.Success(freshReport)
                saveReportToCache(freshReport)
            } catch (e: Exception) {
                println("MainViewModel: Immediate report fetch failed: ${e.message}")
            }
        }
    }

    // ── Cache helpers ───────────────────────────────────────────────────────────

    private suspend fun loadFromCache() {
        try {
            val cachedDb = cacheDao.getJson(CACHE_KEY_DB)
            if (cachedDb != null) {
                val parsed = dbAdapter.fromJson(cachedDb)
                if (parsed != null) {
                    _dbState.value = UiState.Success(parsed)
                }
            }
            val cachedReport = cacheDao.getJson(CACHE_KEY_REPORT)
            if (cachedReport != null) {
                val parsed = reportAdapter.fromJson(cachedReport)
                if (parsed != null) {
                    _reportState.value = UiState.Success(parsed)
                }
            }
            _pendingCount.value = pendingDao.count()
        } catch (e: Exception) {
            println("MainViewModel: Cache load failed: ${e.message}")
        }
    }

    private suspend fun saveDbToCache(data: FullDatabase) {
        try {
            val json = dbAdapter.toJson(data)
            cacheDao.put(CacheEntry(CACHE_KEY_DB, json, System.currentTimeMillis()))
        } catch (e: Exception) {
            println("MainViewModel: Cache save failed: ${e.message}")
        }
    }

    private suspend fun saveReportToCache(data: DailyReport) {
        try {
            val json = reportAdapter.toJson(data)
            cacheDao.put(CacheEntry(CACHE_KEY_REPORT, json, System.currentTimeMillis()))
        } catch (e: Exception) {
            println("MainViewModel: Report cache save failed: ${e.message}")
        }
    }

    // ── Offline operation queue ─────────────────────────────────────────────────

    private suspend fun queueOperation(type: String, payload: String) {
        pendingDao.insert(PendingOperation(type = type, payload = payload))
        _pendingCount.value = pendingDao.count()
    }

    private suspend fun syncPendingOperations() {
        val pending = pendingDao.getAll()
        if (pending.isEmpty()) return

        for (op in pending) {
            try {
                val success = when (op.type) {
                    "add_customer" -> {
                        val req = moshi.adapter(AddCustomerRequest::class.java).fromJson(op.payload)
                        if (req != null) {
                            val response = ApiClient.apiService.addCustomer(req)
                            response.isSuccessful && response.body()?.success == true
                        } else false
                    }
                    "create_bill" -> {
                        val req = moshi.adapter(CreateBillRequest::class.java).fromJson(op.payload)
                        if (req != null) {
                            val response = ApiClient.apiService.createBill(req)
                            response.isSuccessful && response.body()?.success == true
                        } else false
                    }
                    "add_payment" -> {
                        val req = moshi.adapter(AddPaymentRequest::class.java).fromJson(op.payload)
                        if (req != null) {
                            val response = ApiClient.apiService.addPayment(req)
                            response.isSuccessful
                        } else false
                    }
                    "mark_paid" -> {
                        val req = moshi.adapter(MarkBillPaidRequest::class.java).fromJson(op.payload)
                        if (req != null) {
                            val response = ApiClient.apiService.markBillPaid(req)
                            response.isSuccessful && response.body()?.success == true
                        } else false
                    }
                    else -> false
                }
                
                if (success) {
                    pendingDao.delete(op.id)
                } else {
                    println("SyncManager: Operation ${op.type} failed, keeping for retry")
                }
            } catch (e: Exception) {
                pendingDao.incrementRetries(op.id)
                if (op.retries >= MAX_RETRIES) {
                    pendingDao.delete(op.id) // Drop after max retries
                }
                break // Stop syncing on first failure — network may be flaky
            }
        }
        _pendingCount.value = pendingDao.count()
    }

    // ── LiveSync ────────────────────────────────────────────────────────────────

    fun startLiveSyncIfNeeded() {
        if (_authToken.value != null) {
            LiveSyncManager.start()
        }
    }

    fun setSyncInterval(millis: Long) {
        LiveSyncManager.intervalMillis = millis
    }

    // ── Mutations (with offline queue) ──────────────────────────────────────────

    fun addCustomer(context: android.content.Context, name: String, phone: String) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    // Queue for later
                    val payload = moshi.adapter(AddCustomerRequest::class.java)
                        .toJson(AddCustomerRequest(name, phone))
                    queueOperation("add_customer", payload)
                    _operationState.value = OperationState.Success("Customer saved — will sync when online")
                    return@launch
                }

                val response = ApiClient.apiService.addCustomer(AddCustomerRequest(name, phone))
                if (response.isSuccessful && response.body()?.success == true) {
                    _operationState.value = OperationState.Success("Customer added successfully")
                    fetchData(context)
                } else {
                    _operationState.value = OperationState.Error(response.body()?.message ?: "Failed to add customer")
                }
            } catch (e: Exception) {
                // Queue on failure too
                val payload = moshi.adapter(AddCustomerRequest::class.java)
                    .toJson(AddCustomerRequest(name, phone))
                queueOperation("add_customer", payload)
                _operationState.value = OperationState.Success("Customer saved offline — will sync later")
            }
        }
    }

    fun addPayment(context: android.content.Context, customerId: String, amount: Double, note: String?) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    val payload = moshi.adapter(AddPaymentRequest::class.java)
                        .toJson(AddPaymentRequest(customerId, amount, note))
                    queueOperation("add_payment", payload)
                    _operationState.value = OperationState.Success("Payment saved — will sync when online")
                    return@launch
                }

                val response = ApiClient.apiService.addPayment(AddPaymentRequest(customerId, amount, note))
                if (response.isSuccessful) {
                    _operationState.value = OperationState.Success("Payment recorded successfully")
                    fetchData(context)
                } else {
                    _operationState.value = OperationState.Error("Failed to record payment")
                }
            } catch (e: Exception) {
                val payload = moshi.adapter(AddPaymentRequest::class.java)
                    .toJson(AddPaymentRequest(customerId, amount, note))
                queueOperation("add_payment", payload)
                _operationState.value = OperationState.Success("Payment saved offline — will sync later")
            }
        }
    }

    fun createBill(context: android.content.Context, customerId: String, amount: Double, items: List<BillItemRequest>?) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    val payload = moshi.adapter(CreateBillRequest::class.java)
                        .toJson(CreateBillRequest(customerId, amount, items))
                    queueOperation("create_bill", payload)
                    _operationState.value = OperationState.Success("Bill saved — will sync when online")
                    return@launch
                }

                val response = ApiClient.apiService.createBill(CreateBillRequest(customerId, amount, items))
                val billId = response.body()?.billId
                if (response.isSuccessful && !billId.isNullOrBlank()) {
                    _lastCreatedBillId.value = billId
                    _operationState.value = OperationState.Success("Bill created (ID: $billId)")
                    fetchData(context)
                } else {
                    _operationState.value = OperationState.Error("Failed to create bill")
                }
            } catch (e: Exception) {
                val payload = moshi.adapter(CreateBillRequest::class.java)
                    .toJson(CreateBillRequest(customerId, amount, items))
                queueOperation("create_bill", payload)
                _operationState.value = OperationState.Success("Bill saved offline — will sync later")
            }
        }
    }

    fun resetOperationState() {
        _operationState.value = OperationState.Idle
        _lastCreatedBillId.value = null
    }

    fun sendInvoiceOnWhatsApp(context: android.content.Context, billId: String) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _operationState.value = OperationState.Error("No internet connection")
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

    fun sendStatementOnWhatsApp(context: android.content.Context, customerId: String) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _operationState.value = OperationState.Error("No internet connection")
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

    fun sendReminderOnWhatsApp(context: android.content.Context, customerId: String, message: String? = null) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _operationState.value = OperationState.Error("No internet connection")
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

    fun markBillPaid(context: android.content.Context, billId: String) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    val payload = moshi.adapter(MarkBillPaidRequest::class.java)
                        .toJson(MarkBillPaidRequest(billId))
                    queueOperation("mark_paid", payload)
                    _operationState.value = OperationState.Success("Saved offline — will sync when online")
                    return@launch
                }

                val response = ApiClient.apiService.markBillPaid(MarkBillPaidRequest(billId))
                val ok = response.isSuccessful && response.body()?.success == true
                _operationState.value =
                    if (ok) OperationState.Success("Bill marked as paid")
                    else OperationState.Error("Failed to mark bill paid")
                if (ok) fetchData(context)
            } catch (e: Exception) {
                val payload = moshi.adapter(MarkBillPaidRequest::class.java)
                    .toJson(MarkBillPaidRequest(billId))
                queueOperation("mark_paid", payload)
                _operationState.value = OperationState.Success("Saved offline — will sync later")
            }
        }
    }

    // ── Auth ────────────────────────────────────────────────────────────────────

    fun requestLoginCode(storeId: String, phone: String, retryCount: Int = 0) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.requestLoginCode(RequestLoginCodeRequest(storeId, phone))
                if (response.isSuccessful && response.body()?.success == true) {
                    _operationState.value = OperationState.Success("Code sent on WhatsApp")
                } else {
                    val serverMsg = response.body()?.message ?: "Failed to send OTP"
                    _operationState.value = OperationState.Error(serverMsg)
                }
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is java.net.SocketTimeoutException -> {
                        if (retryCount < 2) {
                            // Retry with exponential backoff
                            kotlinx.coroutines.delay((1000L * (retryCount + 1)))
                            requestLoginCode(storeId, phone, retryCount + 1)
                            return@launch
                        } else {
                            "Connection timed out. Please check your internet connection and try again."
                        }
                    }
                    is java.net.UnknownHostException -> {
                        "No internet connection. Please check your network and try again."
                    }
                    is java.net.ConnectException -> {
                        "Unable to connect to server. Please try again."
                    }
                    else -> {
                        "Failed to send OTP: ${e.message}"
                    }
                }
                _operationState.value = OperationState.Error(errorMessage)
            }
        }
    }

    fun verifyLoginCode(storeId: String, phone: String, code: String, context: android.content.Context) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.verifyLoginCode(VerifyLoginCodeRequest(storeId, phone, code))
                val token = response.body()?.token
                if (response.isSuccessful && !token.isNullOrBlank()) {
                    _authToken.value = token
                    com.aistudio.sharmakhata.pqmzvk.util.SessionManager.setToken(context, token)
                    LiveSyncManager.stop()
                    LiveSyncManager.start()
                    _operationState.value = OperationState.Success("Logged in")
                } else {
                    val serverMsg = response.body()?.message ?: "Invalid or expired OTP"
                    _operationState.value = OperationState.Error(serverMsg)
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Network error: ${e.message}")
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
        gstin: String? = null,
        context: android.content.Context? = null,
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
                        gstin = gstin,
                    )
                )
                val storeId = response.body()?.store_id
                val ok = response.isSuccessful && !storeId.isNullOrBlank()
                if (ok) {
                    _registeredStoreId.value = storeId
                    // Save store ID and phone for login
                    if (context != null) {
                        com.aistudio.sharmakhata.pqmzvk.util.SessionManager.saveStoreInfo(context, storeId, phone)
                    }
                    _operationState.value = OperationState.Success("Store registered (ID: $storeId)")
                } else {
                    _operationState.value = OperationState.Error(response.body()?.message ?: "Failed to register store")
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Failed to register store: ${e.message}")
            }
        }
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    fun loadItems() {
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            itemDao.getAllItems().collect { _items.value = it }
        }
    }

    fun saveItem(name: String, price: Double, stock: Int, lowStockAlert: Int, itemId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (itemId != null) {
                itemDao.update(ItemEntity(id = itemId, name = name, price = price, stock = stock, lowStockAlert = lowStockAlert))
            } else {
                itemDao.insert(ItemEntity(name = name, price = price, stock = stock, lowStockAlert = lowStockAlert))
            }
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { itemDao.deleteById(id) }
    }

    fun refreshItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.apiService.getStoredItems()
                response.items.forEach { serverItem ->
                    val existing = itemDao.getAllItemsList().find { it.name == serverItem.name }
                    if (existing != null) {
                        itemDao.update(existing.copy(price = serverItem.lastPrice, lastPrice = serverItem.lastPrice))
                    } else {
                        itemDao.insert(ItemEntity(name = serverItem.name, price = serverItem.lastPrice, lastPrice = serverItem.lastPrice))
                    }
                }
            } catch (e: Exception) { println("refreshItems error: ${e.message}") }
        }
    }

    fun loadStoredItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.apiService.getStoredItems()
                _storedItems.value = response.items
            } catch (e: Exception) { println("loadStoredItems error: ${e.message}") }
        }
    }

    // ── Expenses ──────────────────────────────────────────────────────────────

    fun loadExpenses() {
        expensesJob?.cancel()
        expensesJob = viewModelScope.launch {
            expenseDao.getAllExpenses().collect { _expenses.value = it }
        }
    }

    fun loadTodayTotal() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            val endOfDay = cal.timeInMillis
            expenseDao.getTotalExpensesBetween(startOfDay, endOfDay).collect { _todayTotal.value = it ?: 0.0 }
        }
    }

    fun saveExpense(title: String, amount: Double, category: String, note: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            expenseDao.insert(ExpenseEntity(title = title, amount = amount, category = category, note = note))
            _operationState.value = OperationState.Success("Expense added")
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { expenseDao.deleteById(id) }
    }

    // ── Quick Bill ────────────────────────────────────────────────────────────

    fun quickBill(customerName: String?, customerPhone: String?, total: Double, items: List<BillItemRequest>?) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.createBill(CreateBillRequest(customerId = "walk-in", amount = total, items = items))
                val billId = response.body()?.billId
                if (response.isSuccessful && !billId.isNullOrBlank()) {
                    _lastCreatedBillId.value = billId
                    _operationState.value = OperationState.Success("Bill created (ID: $billId)")
                    fetchData()
                } else {
                    _operationState.value = OperationState.Error("Failed to create bill")
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Failed: ${e.message}")
            }
        }
    }

    companion object {
        private const val CACHE_KEY_DB = "full_database"
        private const val CACHE_KEY_REPORT = "daily_report"
        private const val MAX_RETRIES = 3
    }
}
