package com.aistudio.sharmakhata.pqmzvk.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingDao {
    @Query("SELECT * FROM pending_operations ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingOperation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(op: PendingOperation): Long

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_operations SET retries = retries + 1 WHERE id = :id")
    suspend fun incrementRetries(id: Long)

    @Query("SELECT COUNT(*) FROM pending_operations")
    suspend fun count(): Int

    @Query("DELETE FROM pending_operations")
    suspend fun clearAll()
}
