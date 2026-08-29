package com.expensetracker.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.util.PeriodCalculator
import java.text.NumberFormat
import java.util.Currency

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel(), onMicClick: () -> Unit) {
    val tx by viewModel.transactions.collectAsState()
    var period by remember { mutableStateOf("Month") }
    val fmt = NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance("USD") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ExpenseTracker", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Day","Week","Month","Year").forEach { p ->
                FilterChip(selected = period==p, onClick = { period=p }, label = { Text(p) })
            }
        }
        // KPI
        val total = tx.sumOf { it.amountBaseMinor }
        Card { Column(modifier = Modifier.padding(16.dp)) {
            Text("Total in period: ${fmt.format(total/100.0)}")
            Text("Transactions: ${tx.size}", style = MaterialTheme.typography.labelMedium)
            // Flexible comparison placeholder
            val range = PeriodCalculator.rangeFor(period)
            Text("Period: ${range.first}..${range.last}", style = MaterialTheme.typography.labelSmall)
        }}
        // Charts placeholder (MPAndroidChart would render here)
        Card { Box(modifier = Modifier.fillMaxWidth().height(160.dp).padding(16.dp)) { Text("Pie: by Category | Bar: daily | Line: trend (offline computed via Room SUM GROUP BY)") } }
        Button(onClick = onMicClick, modifier = Modifier.fillMaxWidth()) { Text("Tap to Speak Expense") }
        Divider()
        Text("Recent (offline, encrypted Room):", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tx.take(20)) { t ->
                ListItem(headlineContent = { Text("${t.amountMinor/100.0} ${t.currencyCode} - ${t.category}") }, supportingContent = { Text(t.normalizedText) })
                Divider()
            }
        }
    }
}
