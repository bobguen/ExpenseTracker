package com.expensetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dao: TransactionDao
) : ViewModel() {
    val transactions = dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun sumForRange(start: Long, end: Long): Long = dao.sumInRange(start, end) ?: 0L

    fun comparison(current: LongRange): Pair<Long, Long> {
        // Simplified, actual uses suspend; for UI, compute via dao in compose
        return 0L to 0L
    }
}
