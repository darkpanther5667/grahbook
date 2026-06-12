package com.aistudio.sharmakhata.pqmzvk.data.sync

import android.content.Context
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import com.aistudio.sharmakhata.pqmzvk.data.remote.ApiClient
import com.aistudio.sharmakhata.pqmzvk.util.SessionManager
import java.time.Instant

/**
 * Manages delta sync with exponential backoff, conflict resolution,
 * and graceful fallback to full-sync when the server doesn't support delta.
 *
 * **Conflict resolution**: Server wins. Pending offline operations are
 * replayed before delta sync runs, so conflict risk is minimal.
 *
 * **Backoff**: 5s → 10s → 20s → 40s → 80s → 160s → 300s (max 5 min).
 */
object DeltaSyncManager {

    private const val MAX_BACKOFF_MS = 300_000L
    private const val INITIAL_BACKOFF_MS = 5_000L

    private var consecutiveFailures = 0

    /**
     * Fetch the full database using delta if available, full fetch otherwise.
     * IMPORTANT: On cold start (currentDb == null), always do a full fetch.
     * Delta sync with null currentDb produces an empty database, since the
     * server only returns changes since last sync, not the full data set.
     */
    suspend fun fetch(context: Context, currentDb: FullDatabase?): FullDatabase? {
        // Cold start — no in-memory data, need full database
        if (currentDb == null) return fullFetch(context)

        val since = SessionManager.lastSyncedAt

        if (since.isNullOrBlank()) {
            return fullFetch(context)
        }

        return deltaThenFallback(context, since, currentDb)
    }

    /**
     * Try delta endpoint; if unsupported, fall back to full fetch.
     */
    private suspend fun deltaThenFallback(
        context: Context,
        since: String,
        currentDb: FullDatabase?
    ): FullDatabase? {
        try {
            val response = ApiClient.apiService.getDeltaChanges(since)
            if (response.isSuccessful) {
                val body = try { response.body() } catch (e: Exception) {
                    android.util.Log.w("DeltaSync", "Failed to deserialize DeltaChanges: ${e.message}")
                    null
                }
                if (body != null) {
                    val merged = body.fallbackFullDb?.let { db ->
                        // Server doesn't support delta, sent full as fallback
                        android.util.Log.d("DeltaSync", "Server returned full fallback")
                        db
                    } ?: if (currentDb != null) {
                        android.util.Log.d("DeltaSync", "Applying delta changes")
                        mergeDelta(currentDb, body)
                    } else {
                        // No existing db — rebuild from delta
                        rebuildFromDelta(body)
                    }
                    consecutiveFailures = 0
                    SessionManager.saveLastSyncedAt(context,
                        body.serverTime ?: Instant.now().toString())
                    return merged
                }
            } else if (response.code() == 404 || response.code() == 400) {
                android.util.Log.d("DeltaSync", "Delta endpoint unsupported (${response.code()}) — full fetch")
                return fullFetch(context)
            }
            // Non-404 failure — escalate to catch block
            val err = "Delta endpoint HTTP ${response.code()}: ${response.errorBody()?.string()}"
            return handleFailure(context, err)
        } catch (e: java.net.SocketTimeoutException) {
            return handleFailure(context, "Connection timed out")
        } catch (e: java.net.UnknownHostException) {
            return handleFailure(context, "No internet connection")
        } catch (e: Exception) {
            return handleFailure(context, "Delta sync failed")
        }
    }

    /**
     * Full database fetch (fallback for first sync or when delta unavailable).
     */
    private suspend fun fullFetch(context: Context): FullDatabase? {
        return try {
            val response = ApiClient.apiService.getFullDatabase()
            if (response.isSuccessful) {
                val db = try { response.body() } catch (e: Exception) {
                    android.util.Log.e("DeltaSync", "Failed to deserialize FullDatabase: ${e.message}")
                    null
                }
                if (db != null) {
                    consecutiveFailures = 0
                    // Use server time for lastSyncedAt to avoid clock skew issues.
                    // Subtract 2s as a safety buffer so we never miss changes.
                    val syncTime = db.serverTime?.let { t ->
                        try { Instant.parse(t).minusSeconds(2).toString() }
                        catch (e: Exception) { Instant.now().minusSeconds(2).toString() }
                    } ?: Instant.now().minusSeconds(2).toString()
                    SessionManager.saveLastSyncedAt(context, syncTime)
                    db
                } else {
                    handleFailure(context, "Empty or malformed database response")
                }
            } else {
                handleFailure(context, "Server error ${response.code()}: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            handleFailure(context, "API call failed: ${e.message}")
        }
    }

    /**
     * Safely deserialize a FullDatabase from the cache JSON, returning null on failure.
     */
    fun parseFullDatabase(json: String): FullDatabase? {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(FullDatabase::class.java)
        return try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            android.util.Log.e("DeltaSync", "Failed to parse cached FullDatabase: ${e.message}")
            null
        }
    }

    /**
     * Merge delta changes into the existing database.
     * Server-sent items replace existing ones by ID; deleted IDs are removed.
     */
    private fun mergeDelta(db: FullDatabase, delta: com.aistudio.sharmakhata.pqmzvk.data.model.DeltaChanges): FullDatabase {
        val deleteCustomers = delta.deletedCustomerIds?.toSet() ?: emptySet()
        val updateCustomers = delta.customers?.associateBy { it.id } ?: emptyMap()
        val mergedCustomers = db.customers.filterNot { it.id in deleteCustomers || it.id in updateCustomers } + delta.customers.orEmpty()

        val deleteTxns = delta.deletedTransactionIds?.toSet() ?: emptySet()
        val updateTxns = delta.transactions?.associateBy { it.id } ?: emptyMap()
        val mergedTransactions = db.transactions.filterNot { it.id in deleteTxns || it.id in updateTxns } + delta.transactions.orEmpty()

        val deleteBills = delta.deletedBillIds?.toSet() ?: emptySet()
        val updateBills = delta.bills?.associateBy { it.id } ?: emptyMap()
        val mergedBills = db.bills.filterNot { it.id in deleteBills || it.id in updateBills } + delta.bills.orEmpty()

        return db.copy(
            customers = mergedCustomers,
            transactions = mergedTransactions,
            bills = mergedBills
        )
    }

    private fun rebuildFromDelta(delta: com.aistudio.sharmakhata.pqmzvk.data.model.DeltaChanges): FullDatabase {
        return FullDatabase(
            shop = null,
            customers = delta.customers.orEmpty(),
            transactions = delta.transactions.orEmpty(),
            bills = delta.bills.orEmpty()
        )
    }

    private fun handleFailure(context: Context, error: String): FullDatabase? {
        consecutiveFailures++
        android.util.Log.e("DeltaSync", "Fetch failed (#$consecutiveFailures): $error")
        return null // caller handles backoff
    }

    fun getBackoffMs(): Long {
        if (consecutiveFailures == 0) return 0L
        return (INITIAL_BACKOFF_MS * (1 shl (consecutiveFailures - 1)))
            .coerceAtMost(MAX_BACKOFF_MS)
    }

    fun resetFailures() {
        consecutiveFailures = 0
    }
}
