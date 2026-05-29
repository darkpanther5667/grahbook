package com.aistudio.sharmakhata.pqmzvk.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String = "Other",
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
