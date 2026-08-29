package com.expensetracker.data.remote.exchange

import com.expensetracker.data.local.RateCacheDao
import com.expensetracker.data.local.RateCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RateRepository @Inject constructor(
    private val service: ExchangeRateService,
    private val cacheDao: RateCacheDao
) {
    // Isolated: only base code sent
    suspend fun refresh(base: String) {
        try {
            val resp = service.getLatest(base)
            val now = System.currentTimeMillis()
            resp.rates.forEach { (target, rate) ->
                cacheDao.insert(RateCacheEntity(base, target, rate, now))
            }
        } catch (e: Exception) {
            // Use stale cache, don't crash expense entry
        }
    }

    suspend fun getRate(base: String, target: String): Double? {
        val cached = cacheDao.getRate(base, target)
        // stale if >24h
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMs < 24 * 60 * 60 * 1000) return cached.rate
        return cached?.rate
    }

    fun convert(amountMinor: Long, rate: Double): Long = (amountMinor * rate).toLong()
}
