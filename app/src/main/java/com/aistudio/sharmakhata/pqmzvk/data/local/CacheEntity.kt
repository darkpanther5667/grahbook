package com.aistudio.sharmakhata.pqmzvk.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Generic key-value cache for JSON blobs (FullDatabase, DailyReport).
 */
@Entity(tableName = "cache_entries")
data class CacheEntry(
    @PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long
)

/**
 * Queued operations to replay when network returns.
 */
@Entity(tableName = "pending_operations")
data class PendingOperation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,       // "add_customer", "create_bill", "add_payment", "mark_paid"
    val payload: String,    // JSON-encoded request body
    val createdAt: Long = System.currentTimeMillis(),
    val retries: Int = 0
)
