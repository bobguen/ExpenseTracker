package com.expensetracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val currencyCode: String,
    val amountBaseMinor: Long,
    val baseCurrency: String,
    val rateUsed: Double?,
    val rawText: String,
    val normalizedText: String,
    val category: String,
    val confidence: Float,
    val timestampMs: Long,
    val createdAtMs: Long,
    val needsRateSync: Boolean = false,
    val source: String = "voice"
)

@Entity(tableName = "rate_cache", primaryKeys = ["base", "target"])
data class RateCacheEntity(
    val base: String,
    val target: String,
    val rate: Double,
    val fetchedAtMs: Long
)

@Entity(tableName = "category_overrides", primaryKeys = ["keyword"])
data class CategoryOverrideEntity(
    val keyword: String,
    val category: String
)
