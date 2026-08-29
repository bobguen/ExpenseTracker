package com.expensetracker.data.remote.exchange

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RateSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: RateRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val base = inputData.getString("base") ?: "USD"
        return try {
            repo.refresh(base)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
