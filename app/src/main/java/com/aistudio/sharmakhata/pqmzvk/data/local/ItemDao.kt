package com.aistudio.sharmakhata.pqmzvk.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun getAllItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    suspend fun getAllItemsList(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchItems(query: String): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM items WHERE stock > 0 AND stock <= lowStockAlert ORDER BY stock ASC")
    fun getLowStockItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE stock = 0 ORDER BY name ASC")
    fun getOutOfStockItems(): Flow<List<ItemEntity>>

    @Query("UPDATE items SET stock = stock + :quantity, updatedAt = :now WHERE id = :id")
    suspend fun addStock(id: Long, quantity: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE items SET stock = CASE WHEN stock - :quantity >= 0 THEN stock - :quantity ELSE 0 END, updatedAt = :now WHERE id = :id")
    suspend fun reduceStock(id: Long, quantity: Int, now: Long = System.currentTimeMillis())
}
