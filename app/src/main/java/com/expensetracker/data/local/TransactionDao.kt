package com.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Query("SELECT * FROM transactions ORDER BY timestampMs DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT category, SUM(amountBaseMinor) as total FROM transactions WHERE timestampMs BETWEEN :start AND :end GROUP BY category ORDER BY total DESC")
    fun spendByCategory(start: Long, end: Long): Flow<List<CategoryTotal>>

    @Query("SELECT SUM(amountBaseMinor) FROM transactions WHERE timestampMs BETWEEN :start AND :end")
    suspend fun sumInRange(start: Long, end: Long): Long?

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}

data class CategoryTotal(val category: String, val total: Long)

@Dao
interface RateCacheDao {
    @Query("SELECT * FROM rate_cache WHERE base = :base AND target = :target")
    suspend fun getRate(base: String, target: String): RateCacheEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RateCacheEntity)

    @Query("SELECT * FROM rate_cache")
    fun getAll(): Flow<List<RateCacheEntity>>
}
