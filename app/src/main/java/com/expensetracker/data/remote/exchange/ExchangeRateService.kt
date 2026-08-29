package com.expensetracker.data.remote.exchange

import retrofit2.http.GET
import retrofit2.http.Path

// Isolated network - ONLY base currency sent, no transaction data
interface ExchangeRateService {
    @GET("latest/{base}")
    suspend fun getLatest(@Path("base") base: String): RateResponse
}

data class RateResponse(
    val base: String,
    val rates: Map<String, Double>,
    val time_last_update_unix: Long
)
