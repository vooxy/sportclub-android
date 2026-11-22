package com.tisbi.sportclub.ui.trainer

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tisbi.sportclub.db.User
import com.tisbi.sportclub.repo.Database
import com.tisbi.sportclub.ui.common.EmptyState
import com.tisbi.sportclub.ui.common.fmtDate
import com.tisbi.sportclub.ui.common.fmtDateTimeHuman
import com.tisbi.sportclub.ui.common.fmtTime
import kotlinx.coroutines.launch

/** Интерфейс тренера: своё расписание и отметка посещений (ТЗ п. 4.2, 4.2.5) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerScreen(user: User, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val trainer = remember { user.idTrainer?.let { Database.trainers.byId(it) } }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Рабочее место тренера") },
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
                    icon = { Icon(Icons.Default.CalendarMonth, null) },
                    label = { Text("Мои занятия") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.CheckCircle, null) },
                    label = { Text("Отметки") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (tab == 0) TrainerSessionsTab(user) { notify(it) }
            else TrainerProfileTab(trainer?.fio ?: user.fullName, trainer?.specialization, trainer?.phone)
        }
    }
}

@Composable
private fun TrainerSessionsTab(user: User, notify: (String) -> Unit) {
    var showPast by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var openSession by remember { mutableStateOf<Long?>(null) }

    val sessions = remember(refresh, showPast) {
        Database.schedule.sessions(
            trainerId = user.idTrainer,
            includePast = showPast
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showPast, onClick = { showPast = false },
                label = { Text("Предстоящие") }
            )
            FilterChip(
                selected = showPast, onClick = { showPast = true },
                label = { Text("Проведённые") }
            )
        }
        Spacer(Modifier.height(10.dp))
        if (sessions.isEmpty()) {
            EmptyState(if (showPast) "Проведённых занятий нет" else "Предстоящих занятий нет")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
            ) {
                items(sessions, key = { it.session.id }) { s ->
                    Card(
                        onClick = { openSession = s.session.id },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(s.session.sessionName, fontWeight = FontWeight.Medium)
                                Text(
                                    fmtTime(s.session.startDateTime),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                fmtDateTimeHuman(s.session.startDateTime).substringBefore(", ${fmtTime(s.session.startDateTime)}"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${s.session.hall} • записалось: ${s.bookedCount} из ${s.session.maxParticipants}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    openSession?.let { sessionId ->
        val details = remember(sessionId) { Database.schedule.byId(sessionId) }
        details?.let { session ->
            AttendanceMarkDialog(
                sessionId = sessionId,
                title = session.sessionName,
                subtitle = fmtDateTimeHuman(session.startDateTime),
                onClose = { openSession = null },
                onChanged = {
                    refresh++
                    notify(it)
                }
            )
        }
    }
}

/** Отметка посещений на групповом занятии (ТЗ п. 4.2.5) */
@Composable
private fun AttendanceMarkDialog(
    sessionId: Long,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    onChanged: (String) -> Unit
) {
    var refresh by remember { mutableIntStateOf(0) }
    val bookings = remember(refresh) { Database.attendance.forSession(sessionId) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (bookings.isEmpty()) {
                    Text("На занятие пока никто не записан")
                } else {
                    LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(bookings, key = { it.attendance.id }) { b ->
                            val visited = b.attendance.status == com.tisbi.sportclub.db.AttendanceStatus.VISITED
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(b.clientFio, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                    IconButton(onClick = {
                                        Database.attendance.markVisited(b.attendance.id, !visited)
                                        refresh++
                                        onChanged(
                                            if (!visited) "Отмечено: ${b.clientFio} пришёл"
                                            else "Отметка снята: ${b.clientFio}"
                                        )
                                    }) {
                                        Icon(
                                            if (visited) Icons.Default.CheckCircle
                                            else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (visited) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Готово") }
        }
    )
}

@Composable
private fun TrainerProfileTab(fio: String, specialization: String?, phone: String?) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text(fio, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                com.tisbi.sportclub.ui.common.KeyValueRow("Специализация", specialization ?: "—")
                com.tisbi.sportclub.ui.common.KeyValueRow("Телефон", phone ?: "—")
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {}) { Text("Инструкции по работе с системой недоступны") }
    }
}
