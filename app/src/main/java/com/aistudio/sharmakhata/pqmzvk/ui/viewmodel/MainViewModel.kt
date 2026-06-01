package com.aistudio.sharmakhata.pqmzvk.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.aistudio.sharmakhata.pqmzvk.data.local.AppDatabase
import com.aistudio.sharmakhata.pqmzvk.data.local.CacheEntry
import com.aistudio.sharmakhata.pqmzvk.data.local.PendingOperation
import com.aistudio.sharmakhata.pqmzvk.data.model.DailyReport
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import com.aistudio.sharmakhata.pqmzvk.data.remote.AddCustomerRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.AddPaymentRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.ApiClient
import com.aistudio.sharmakhata.pqmzvk.data.remote.BillItemRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.CreateBillRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.MarkBillPaidRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.RequestLoginCodeRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.VerifyLoginCodeRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.RegisterStoreRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.LoginWithPasswordRequest
import com.aistudio.sharmakhata.pqmzvk.data.sync.DeltaSyncManager
import com.aistudio.sharmakhata.pqmzvk.util.NetworkUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val db = AppDatabase.get(getApplication())
    private val cacheDao = db.cacheDao()
    private val pendingDao = db.pendingDao()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val dbAdapter = moshi.adapter(FullDatabase::class.java)
    private val reportAdapter = moshi.adapter(DailyReport::class.java)

    // ══ Collector Job References (prevent duplicate collector leak) ══════════════
    private var dbCollectorJob: kotlinx.coroutines.Job? = null
    private var reportCollectorJob: kotlinx.coroutines.Job? = null
    private var syncErrorCollectorJob: kotlinx.coroutines.Job? = null

    // ══ Generation counter prevents stale data from overwriting fresh data ══════
    // Incremented on each fetchData() call; only writes matching the current
    // generation are applied, so slow cache loads can't overwrite a newer fetch.
    private var fetchGeneration = 0L

    private val _dbState = MutableStateFlow<UiState<FullDatabase>>(UiState.Loading)
    val dbState: StateFlow<UiState<FullDatabase>> = _dbState.asStateFlow()

    private val _reportState = MutableStateFlow<UiState<DailyReport>>(UiState.Loading)
    val reportState: StateFlow<UiState<DailyReport>> = _reportState.asStateFlow()

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()

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

    private val _droppedOps = MutableStateFlow<List<DroppedOperation>>(emptyList())
    val droppedOps: StateFlow<List<DroppedOperation>> = _droppedOps.asStateFlow()

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

    // ── Cache-first data loading with delta sync ────────────────────────────────

    fun fetchData(context: android.content.Context? = null) {
        _syncError.value = null
        fetchGeneration++ // invalidate any in-flight operations from prior calls
        val gen = fetchGeneration

        if (context != null) {
            LiveSyncManager.init(context)
        }

        // Cancel previous collector jobs to prevent duplicate collectors
        dbCollectorJob?.cancel()
        reportCollectorJob?.cancel()
        syncErrorCollectorJob?.cancel()

        // Step 1: Load from cache immediately (instant, no network needed)
        viewModelScope.launch(Dispatchers.IO) {
            if (gen != fetchGeneration) return@launch
            loadFromCache()
        }

        // Step 2: Check network — if offline, we're done (cache is shown)
        val ctx = context ?: return
        if (!NetworkUtils.isNetworkAvailable(ctx)) {
            _isOffline.value = true
            _syncError.value = "You're offline — showing saved data"
            return
        }
        _isOffline.value = false

        // Step 3: Sync pending offline operations first
        viewModelScope.launch(Dispatchers.IO) {
            if (gen != fetchGeneration) return@launch
            syncPendingOperations()
        }

        // Step 4: Ensure LiveSyncManager is running for continuous polling
        LiveSyncManager.start()

        // Collect ongoing LiveSyncManager updates (only if generation still matches)
        dbCollectorJob = viewModelScope.launch {
            LiveSyncManager.fullDatabase.collect { data ->
                if (data != null && gen == fetchGeneration) {
                    _dbState.value = UiState.Success(data)
                    saveDbToCache(data)
                }
            }
        }
        reportCollectorJob = viewModelScope.launch {
            LiveSyncManager.dailyReport.collect { report ->
                if (report != null && gen == fetchGeneration) {
                    _reportState.value = UiState.Success(report)
                    saveReportToCache(report)
                }
            }
        }
        syncErrorCollectorJob = viewModelScope.launch {
            LiveSyncManager.syncError.collect { error ->
                if (error != null && gen == fetchGeneration) {
                    _syncError.value = error
                }
            }
        }

        // Step 5: Immediate one-shot delta fetch — critical after mutations
        viewModelScope.launch(Dispatchers.IO) {
            if (gen != fetchGeneration) return@launch
            val currentDb = _dbState.value.let {
                if (it is UiState.Success) it.data else null
            }
            val freshDb = DeltaSyncManager.fetch(ctx, currentDb)
            if (freshDb != null && gen == fetchGeneration) {
                _dbState.value = UiState.Success(freshDb)
                saveDbToCache(freshDb)
            } else if (gen == fetchGeneration) {
                android.util.Log.d("MainViewModel", "Immediate delta fetch failed")
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (gen != fetchGeneration) return@launch
            try {
                val response = ApiClient.apiService.getDailyReport()
                if (response.isSuccessful) {
                    val freshReport = response.body()
                    if (freshReport != null && gen == fetchGeneration) {
                        _reportState.value = UiState.Success(freshReport)
                        saveReportToCache(freshReport)
                    }
                } else {
                    android.util.Log.d("MainViewModel", "Immediate report fetch failed: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.d("MainViewModel", "Immediate report fetch failed: ${e.message}")
            }
        }
    }

    // ── Cache helpers ───────────────────────────────────────────────────────────

    private suspend fun loadFromCache() {
        try {
            val cachedDb = cacheDao.getJson(CACHE_KEY_DB)
            if (cachedDb != null) {
                val parsed = try { dbAdapter.fromJson(cachedDb) } catch (e: Exception) {
                    android.util.Log.w("MainViewModel", "Failed to parse cached FullDatabase: ${e.message}")
                    null
                }
                if (parsed != null) {
                    _dbState.value = UiState.Success(parsed)
                }
            }
            val cachedReport = cacheDao.getJson(CACHE_KEY_REPORT)
            if (cachedReport != null) {
                val parsed = try { reportAdapter.fromJson(cachedReport) } catch (e: Exception) {
                    android.util.Log.w("MainViewModel", "Failed to parse cached report: ${e.message}")
                    null
                }
                if (parsed != null) {
                    _reportState.value = UiState.Success(parsed)
                }
            }
            _pendingCount.value = pendingDao.count()
        } catch (e: Exception) {
            android.util.Log.d("MainViewModel", "Cache load failed: ${e.message}")
        }
    }

    private suspend fun saveDbToCache(data: FullDatabase) {
        try {
            val json = dbAdapter.toJson(data)
            cacheDao.put(CacheEntry(CACHE_KEY_DB, json, System.currentTimeMillis()))
        } catch (e: Exception) {
            android.util.Log.d("MainViewModel", "Cache save failed: ${e.message}")
        }
    }

    private suspend fun saveReportToCache(data: DailyReport) {
        try {
            val json = reportAdapter.toJson(data)
            cacheDao.put(CacheEntry(CACHE_KEY_REPORT, json, System.currentTimeMillis()))
        } catch (e: Exception) {
            android.util.Log.d("MainViewModel", "Report cache save failed: ${e.message}")
        }
    }

    // ── Offline operation queue ─────────────────────────────────────────────────

    suspend fun queueOperation(type: String, payload: String) {
        pendingDao.insert(PendingOperation(type = type, payload = payload))
        _pendingCount.value = pendingDao.count()
    }

    suspend fun syncPendingOperations() {
        val pending = pendingDao.getAll().sortedBy { it.createdAt }
        if (pending.isEmpty()) return

        val newDropped = mutableListOf<DroppedOperation>()

        for (op in pending) {
            if (op.retries >= MAX_RETRIES) {
                val drop = DroppedOperation(
                    type = op.type,
                    payload = op.payload,
                    error = "Exceeded $MAX_RETRIES retries — dropped after repeated failures"
                )
                newDropped.add(drop)
                pendingDao.delete(op.id)
                android.util.Log.w("SyncManager", "Dropping operation ${op.type} (id=${op.id}) after $MAX_RETRIES failed retries")
                _syncError.value = "A pending ${op.type} was dropped after $MAX_RETRIES failed attempts — data may be incomplete"
                continue
            }

            try {
                val success = when (op.type) {
                    "add_customer" -> {
                        val req = moshi.adapter(AddCustomerRequest::class.java).fromJson(op.payload)
                        if (req != null) {
                            val response = ApiClient.apiService.addCustomer(req)
                            if (response.code() == 409) {
                                android.util.Log.w("SyncManager", "Conflict adding customer: ${req.name}")
                                true
                            } else {
                                response.isSuccessful && response.body()?.success == true
                            }
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
                    android.util.Log.d("SyncManager", "Operation ${op.type} failed, keeping for retry")
                }
            } catch (e: Exception) {
                pendingDao.incrementRetries(op.id)
                android.util.Log.d("SyncManager", "Operation ${op.type} failed (retry ${op.retries + 1}/$MAX_RETRIES): ${e.message}")
            }
        }

        _pendingCount.value = pendingDao.count()
        if (newDropped.isNotEmpty()) {
            _droppedOps.value = _droppedOps.value + newDropped
        }
    }

    // ── LiveSync ────────────────────────────────────────────────────────────────

    fun startLiveSyncIfNeeded(context: android.content.Context? = null) {
        if (_authToken.value != null) {
            if (context != null) LiveSyncManager.init(context)
            LiveSyncManager.start()
        }
    }

    fun setSyncInterval(millis: Long) {
        LiveSyncManager.intervalMillis = millis
    }

    fun resetOperationState() {
        _operationState.value = OperationState.Idle
    }

    // ── Auth ────────────────────────────────────────────────────────────────────

    fun requestLoginCode(phone: String, storeId: String? = null, retryCount: Int = 0) {
        val sid = storeId.takeIf { !it.isNullOrBlank() } ?: com.aistudio.sharmakhata.pqmzvk.util.SessionManager.storeId ?: ""
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.requestLoginCode(RequestLoginCodeRequest(sid, phone))
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
                            kotlinx.coroutines.delay((1000L * (retryCount + 1)))
                            requestLoginCode(phone, sid, retryCount + 1)
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
                        "Failed to send OTP. Please try again."
                    }
                }
                _operationState.value = OperationState.Error(errorMessage)
            }
        }
    }

    fun verifyLoginCode(phone: String, code: String, context: android.content.Context, storeId: String? = null) {
        val sid = storeId.takeIf { !it.isNullOrBlank() } ?: com.aistudio.sharmakhata.pqmzvk.util.SessionManager.storeId ?: ""
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.verifyLoginCode(VerifyLoginCodeRequest(sid, phone, code))
                val body = response.body()
                val token = body?.token
                if (response.isSuccessful && !token.isNullOrBlank()) {
                    _authToken.value = token
                    com.aistudio.sharmakhata.pqmzvk.util.SessionManager.setToken(context, token)

                    val storeMap = body?.store as? Map<*, *>
                    val storeIdFromServer = storeMap?.get("id") as? String
                    if (!storeIdFromServer.isNullOrBlank()) {
                        com.aistudio.sharmakhata.pqmzvk.util.SessionManager.saveStoreInfo(context, storeIdFromServer, phone)
                        com.aistudio.sharmakhata.pqmzvk.util.SessionManager.reload(context)
                    }

                    LiveSyncManager.stop()
                    LiveSyncManager.init(context)
                    LiveSyncManager.start()
                    _operationState.value = OperationState.Success("Logged in")
                } else {
                    val serverMsg = body?.message ?: "Invalid or expired OTP"
                    _operationState.value = OperationState.Error(serverMsg)
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Network error. Please check your connection.")
            }
        }
    }

    fun loginWithPassword(phone: String, password: String, context: android.content.Context) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.loginWithPassword(LoginWithPasswordRequest(phone, password))
                val body = response.body()
                if (response.isSuccessful && body?.success == true && !body.token.isNullOrBlank()) {
                    _authToken.value = body.token
                    com.aistudio.sharmakhata.pqmzvk.util.SessionManager.setToken(context, body.token)

                    val storeMap = body.store as? Map<*, *>
                    val storeIdFromServer = storeMap?.get("id") as? String
                    if (!storeIdFromServer.isNullOrBlank()) {
                        com.aistudio.sharmakhata.pqmzvk.util.SessionManager.saveStoreInfo(context, storeIdFromServer, phone)
                        com.aistudio.sharmakhata.pqmzvk.util.SessionManager.reload(context)
                    }

                    LiveSyncManager.stop()
                    LiveSyncManager.init(context)
                    LiveSyncManager.start()
                    _operationState.value = OperationState.Success("Logged in")
                } else {
                    val serverMsg = body?.message ?: "Login failed. Check your phone and password."
                    _operationState.value = OperationState.Error(serverMsg)
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Network error. Please check your connection.")
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
        password: String? = null,
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
                        password = password,
                    )
                )
                val body = response.body()
                val storeId = body?.store_id
                val status = body?.status

                if (!storeId.isNullOrBlank()) {
                    _registeredStoreId.value = storeId
                    if (context != null) {
                        com.aistudio.sharmakhata.pqmzvk.util.SessionManager.saveStoreInfo(context, storeId, phone)
                        com.aistudio.sharmakhata.pqmzvk.util.SessionManager.reload(context)
                    }
                    val msg = when (status) {
                        "exists" -> "Store already exists — you can login now"
                        else -> body?.message ?: "Store registered"
                    }
                    _operationState.value = OperationState.Success(msg)
                } else {
                    _operationState.value = OperationState.Error(body?.message ?: "Failed to register store")
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Failed to register store. Please try again.")
            }
        }
    }

    fun updateStoreProfile(
        storeName: String,
        ownerName: String,
        address: String?,
        upiId: String?,
        gstin: String?,
        context: android.content.Context
    ) {
        _operationState.value = OperationState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.updateStoreProfile(
                    com.aistudio.sharmakhata.pqmzvk.data.remote.UpdateStoreProfileRequest(
                        store_name = storeName,
                        owner_name = ownerName,
                        address = address,
                        upi_id = upiId,
                        gstin = gstin
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    _operationState.value = OperationState.Success("Store profile updated successfully")
                    // Force refresh to pull updated store info
                    LiveSyncManager.forceRefresh()
                    fetchData(context)
                } else {
                    val serverMsg = response.body()?.message ?: "Failed to update profile"
                    _operationState.value = OperationState.Error(serverMsg)
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Network error. Failed to update profile.")
            }
        }
    }

    companion object {
        const val CACHE_KEY_DB = "full_database"
        const val CACHE_KEY_REPORT = "daily_report"
        const val MAX_RETRIES = 3
    }
}
