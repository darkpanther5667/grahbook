package com.aistudio.sharmakhata.pqmzvk.ui.viewmodel

import com.aistudio.sharmakhata.pqmzvk.data.model.DailyReport
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import com.aistudio.sharmakhata.pqmzvk.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

object LiveSyncManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var syncJob: Job? = null
    private var consecutiveFailures = 0
    private val maxConsecutiveFailures = 3

    // Default interval 5 seconds (configurable via settings later)
    // Increased to 30 seconds to reduce load on server during cold starts
    var intervalMillis: Long = 30_000L

    private val _dailyReport = MutableStateFlow<DailyReport?>(null)
    val dailyReport: StateFlow<DailyReport?> = _dailyReport.asStateFlow()

    private val _fullDatabase = MutableStateFlow<FullDatabase?>(null)
    val fullDatabase: StateFlow<FullDatabase?> = _fullDatabase.asStateFlow()

    private val _lastSynced = MutableStateFlow<Instant?>(null)
    val lastSynced: StateFlow<Instant?> = _lastSynced.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    fun start() {
        if (syncJob != null) return // already running
        println("LiveSyncManager: Starting sync with interval ${intervalMillis}ms")
        _syncError.value = null // Clear any previous errors
        consecutiveFailures = 0
        syncJob = scope.launch {
            println("LiveSyncManager: Sync loop started")
            while (isActive) {
                try {
                    println("LiveSyncManager: Starting API fetch (attempt ${consecutiveFailures + 1})")
                    val report = ApiClient.apiService.getDailyReport()
                    println("LiveSyncManager: Daily report fetched successfully")
                    val db = ApiClient.apiService.getFullDatabase()
                    println("LiveSyncManager: Full database fetched successfully")
                    _dailyReport.value = report
                    _fullDatabase.value = db
                    _lastSynced.value = Instant.now()
                    _syncError.value = null // Clear error on successful sync
                    consecutiveFailures = 0 // Reset failure counter on success
                    println("LiveSyncManager: Sync completed successfully")
                } catch (e: Exception) {
                    consecutiveFailures++
                    // Log error and notify UI
                    val errorMessage = when (e) {
                        is java.net.SocketTimeoutException -> "Connection timed out. Server may be waking up (consecutive failures: $consecutiveFailures/$maxConsecutiveFailures)"
                        is java.net.UnknownHostException -> "No internet connection."
                        else -> "Sync failed: ${e.message} (consecutive failures: $consecutiveFailures/$maxConsecutiveFailures)"
                    }
                    _syncError.value = errorMessage
                    println("LiveSyncManager error: ${e.message}")
                    println("LiveSyncManager error details: ${e.stackTraceToString()}")

                    // Implement exponential backoff for consecutive failures
                    val backoffTime = if (consecutiveFailures > 1) {
                        (intervalMillis * consecutiveFailures).coerceAtMost(300_000) // Max 5 minutes
                    } else {
                        intervalMillis
                    }
                    println("LiveSyncManager: Backing off for ${backoffTime}ms due to error")
                    delay(backoffTime)
                    continue // Skip the normal delay and continue immediately with backoff
                }
                println("LiveSyncManager: Waiting ${intervalMillis}ms before next sync")
                delay(intervalMillis)
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
        _syncError.value = null
        consecutiveFailures = 0
    }
}
