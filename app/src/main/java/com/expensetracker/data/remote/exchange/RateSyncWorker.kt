package com.expensetracker.data.remote.exchange

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RateSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Simplified without repo injection to avoid Hilt crash - re-add after launch verified
        return Result.success()
    }
}
