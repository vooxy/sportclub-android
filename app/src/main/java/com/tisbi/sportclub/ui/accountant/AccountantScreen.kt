package com.tisbi.sportclub.ui.accountant

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tisbi.sportclub.db.User
import com.tisbi.sportclub.repo.Database
import com.tisbi.sportclub.ui.common.StatusChip
import com.tisbi.sportclub.ui.common.fmtDate
import com.tisbi.sportclub.ui.common.fmtDateTimeHuman
import com.tisbi.sportclub.ui.common.money
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Интерфейс бухгалтерии: платежи и финансовая отчётность (ТЗ п. 4.2.4) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountantScreen(user: User, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Бухгалтерия") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                ),
                actions = {
                    TextButton(onClick = onLogout) { Text("Выйти", color = Color.White) }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.RequestQuote, null) },
                    label = { Text("Платежи") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Summarize, null) },
                    label = { Text("Отчёты") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (tab == 0) PaymentsTab() else ReportsTab(::notify)
        }
    }
}

@Composable
private fun PaymentsTab() {
    var period by remember { mutableIntStateOf(1) } // 0 все, 1 день, 2 неделя, 3 месяц
    val from = when (period) {
        1 -> LocalDate.now()
        2 -> LocalDate.now().minusDays(7)
        3 -> LocalDate.now().minusMonths(1)
        else -> null
    }
    val payments = remember(period) { Database.payments.all(from = from) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = period == 0, onClick = { period = 0 }, label = { Text("Все") })
            FilterChip(selected = period == 1, onClick = { period = 1 }, label = { Text("Сегодня") })
            FilterChip(selected = period == 2, onClick = { period = 2 }, label = { Text("Неделя") })
            FilterChip(selected = period == 3, onClick = { period = 3 }, label = { Text("Месяц") })
        }
        Spacer(Modifier.height(10.dp))
        if (payments.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Платежи не найдены", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
            ) {
                items(payments, key = { it.payment.id }) { p ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(p.clientFio, fontWeight = FontWeight.Medium)
                                StatusChip(p.payment.status ?: "")
                            }
                            Text(
                                "${p.membershipTypeName.ifBlank { "—" }} • ${fmtDateTimeHuman(p.payment.paymentDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    p.payment.paymentMethod ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    money(p.payment.amount),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Финансовый отчёт за период (ТЗ п. 4.2.4) с выгрузкой CSV */
@Composable
private fun ReportsTab(notify: (String) -> Unit) {
    val context = LocalContext.current
    var period by remember { mutableIntStateOf(2) } // 1 месяц, 2 квартал(90д), 3 год
    val today = LocalDate.now()
    val from = when (period) {
        1 -> today.withDayOfMonth(1)
        2 -> today.minusDays(90)
        else -> today.minusMonths(12)
    }
    val report = remember(period) { Database.payments.report(from, today) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = period == 1, onClick = { period = 1 }, label = { Text("Месяц") })
            FilterChip(selected = period == 2, onClick = { period = 2 }, label = { Text("90 дней") })
            FilterChip(selected = period == 3, onClick = { period = 3 }, label = { Text("Год") })
        }

        Card(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Общая сумма поступлений",
                    color = Color(0xFFB8B8D8),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    money(report.total),
                    color = Color(0xFFFFD700),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Платежей: ${report.count} • ${fmtDate(from.toString())} — ${fmtDate(today.toString())}",
                    color = Color(0xFFB8B8D8),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text("По способам оплаты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        report.byMethod.forEach { (method, sum) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(method)
                Text(money(sum), fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("По типам абонементов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        report.byType.forEach { (type, sum) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(type)
                Text(money(sum), fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = {
                // Выгрузка отчёта в CSV (ТЗ п. 4.2.4: экспорт в распространённых форматах)
                val csv = buildString {
                    appendLine("Финансовый отчёт ИС Спортивный клуб")
                    appendLine("Период;$from;$today")
                    appendLine()
                    appendLine("Способ оплаты;Сумма")
                    report.byMethod.forEach { (m, s) -> appendLine("$m;${s.toInt()}") }
                    appendLine()
                    appendLine("Тип абонемента;Сумма")
                    report.byType.forEach { (t, s) -> appendLine("$t;${s.toInt()}") }
                    appendLine()
                    appendLine("ИТОГО;${report.total.toInt()}")
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Финансовый отчёт спортивного клуба")
                    putExtra(Intent.EXTRA_TEXT, csv)
                }
                context.startActivity(Intent.createChooser(intent, "Отправить отчёт"))
                notify("Отчёт сформирован. Выберите способ сохранения.")
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PictureAsPdf, null)
            Spacer(Modifier.padding(4.dp))
            Text("Выгрузить отчёт (CSV)")
        }
    }
}
