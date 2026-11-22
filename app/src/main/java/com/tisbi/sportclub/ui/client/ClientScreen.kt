package com.tisbi.sportclub.ui.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tisbi.sportclub.db.AttendanceStatus
import com.tisbi.sportclub.db.Client
import com.tisbi.sportclub.db.MembershipStatus
import com.tisbi.sportclub.db.User
import com.tisbi.sportclub.repo.Database
import com.tisbi.sportclub.repo.MembershipRepo
import com.tisbi.sportclub.ui.common.StatusChip
import com.tisbi.sportclub.ui.common.fmtDateTimeHuman
import com.tisbi.sportclub.ui.common.plural
import kotlinx.coroutines.launch

/** Интерфейс клиента (ТЗ п. 4.1.1: тонкий клиент мобильного приложения) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(user: User, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Текущий клиент, привязанный к учётной записи
    var client by remember { mutableStateOf(Database.clients.byId(user.idClient ?: -1)) }
    var refreshTick by remember { mutableIntStateOf(0) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Спортивный клуб") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    Text(
                        client?.fio ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                clientTabs().forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.second, contentDescription = null) },
                        label = { Text(t.first) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> ClientHomeTab(client) { tab = 1 }
                1 -> ScheduleTab(client) { message, reload ->
                    notify(message)
                    if (reload) {
                        client = Database.clients.byId(user.idClient ?: -1)
                        refreshTick++
                    }
                }
                2 -> MyBookingsTab(client) { notify(it) }
                else -> ProfileTab(client, user, onLogout) { notify(it) }
            }
        }
    }
}

private fun clientTabs(): List<Pair<String, ImageVector>> = listOf(
    "Главная" to Icons.Default.Home,
    "Расписание" to Icons.Default.CalendarMonth,
    "Записи" to Icons.Default.ConfirmationNumber,
    "Профиль" to Icons.Default.Person
)

// ----------------------------------------------------------------------
// Главная: статус абонемента и ближайшие записи
// ----------------------------------------------------------------------

@Composable
private fun ClientHomeTab(client: Client?, onGoToSchedule: () -> Unit) {
    val repo = remember { MembershipRepo() }
    var memberships by remember { mutableStateOf(client?.let { repo.forClient(it.id) } ?: emptyList()) }
    var bookings by remember {
        mutableStateOf(client?.let { Database.attendance.forClient(it.id, upcomingOnly = true) } ?: emptyList())
    }
    val active = memberships.firstOrNull { it.membership.status == MembershipStatus.ACTIVE && it.daysLeft >= 0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Карточка абонемента с золотым градиентом
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1A1A6E), Color(0xFF000080), Color(0xFF28288F))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            "Мой абонемент",
                            color = Color(0xFFB8B8D8),
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (active != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                active.typeName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Действует до", color = Color(0xFFB8B8D8), style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        com.tisbi.sportclub.ui.common.fmtDateHuman(active.membership.endDate),
                                        color = Color(0xFFFFD700),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Осталось", color = Color(0xFFB8B8D8), style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        "${active.daysLeft} ${plural(active.daysLeft, "день", "дня", "дней")}",
                                        color = Color(0xFF40E0D0),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Действующий абонемент отсутствует",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Оформите абонемент в разделе «Профиль», чтобы записываться на занятия",
                                color = Color(0xFFB8B8D8),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onGoToSchedule,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Записаться")
                }
                OutlinedButton(
                    onClick = onGoToSchedule,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Расписание")
                }
            }
        }

        item {
            Text(
                "Ближайшие занятия",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (bookings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "Нет активных записей. Откройте расписание и запишитесь на занятие.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(bookings) { b ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(b.sessionName ?: "Свободное посещение", fontWeight = FontWeight.SemiBold)
                            Text(
                                buildString {
                                    append(fmtDateTimeHuman(b.attendance.visitDateTime ?: b.startDateTime))
                                    if (!b.hall.isNullOrBlank()) append(" • ${b.hall}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusChip(b.attendance.status)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Мои записи: предстоящие и история посещений
// ----------------------------------------------------------------------

@Composable
private fun MyBookingsTab(client: Client?, notify: (String) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val upcoming = remember(refresh) {
        client?.let { Database.attendance.forClient(it.id, upcomingOnly = true) } ?: emptyList()
    }
    val history = remember(refresh) {
        client?.let { Database.attendance.forClient(it.id) } ?: emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Предстоящие записи", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (upcoming.isEmpty()) {
            item { Text("Нет предстоящих записей", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(upcoming) { b ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(b.sessionName ?: "Посещение", fontWeight = FontWeight.SemiBold)
                            StatusChip(b.attendance.status)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            fmtDateTimeHuman(b.attendance.visitDateTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!b.trainerFio.isNullOrBlank()) {
                            Text(
                                "Тренер: ${b.trainerFio}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!b.hall.isNullOrBlank()) {
                            Text(
                                "Зал: ${b.hall}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                Database.attendance.cancelBooking(b.attendance.id)
                                refresh++
                                notify("Запись отменена")
                            },
                            modifier = Modifier.height(38.dp)
                        ) { Text("Отменить запись") }
                    }
                }
            }
        }

        item {
            Text(
                "История посещений",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        val past = history.filter { it.attendance.status != AttendanceStatus.BOOKED }
        if (past.isEmpty()) {
            item { Text("История пуста", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(past) { b ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (b.sessionName.isNullOrBlank()) "Свободное посещение (турникет)" else b.sessionName!!,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                fmtDateTimeHuman(b.attendance.visitDateTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusChip(b.attendance.status)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Профиль: данные клиента, абонементы, покупка, выход
// ----------------------------------------------------------------------

@Composable
private fun ProfileTab(clientParam: Client?, user: User, onLogout: () -> Unit, notify: (String) -> Unit) {
    val client = clientParam ?: run {
        Text("Профиль клиента не найден", modifier = Modifier.padding(16.dp))
        return
    }
    var refresh by remember { mutableIntStateOf(0) }
    val repo = remember { MembershipRepo() }
    val memberships = remember(refresh) { repo.forClient(client.id) }
    var showPurchase by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text(client.fioFull, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                com.tisbi.sportclub.ui.common.KeyValueRow("Номер карты", client.cardNumber ?: "—")
                com.tisbi.sportclub.ui.common.KeyValueRow("Телефон", client.phone)
                com.tisbi.sportclub.ui.common.KeyValueRow("Электронная почта", client.email ?: "—")
                com.tisbi.sportclub.ui.common.KeyValueRow("Дата рождения", com.tisbi.sportclub.ui.common.fmtDate(client.birthDate))
                com.tisbi.sportclub.ui.common.KeyValueRow("Дата регистрации", com.tisbi.sportclub.ui.common.fmtDate(client.registrationDate))
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { showPurchase = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.CreditCard, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (memberships.any { it.membership.status == MembershipStatus.ACTIVE }) "Продлить абонемент" else "Купить абонемент")
        }

        Text(
            "Мои абонементы",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
        )
        memberships.forEach { m ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.typeName, fontWeight = FontWeight.Medium)
                        Text(
                            "${com.tisbi.sportclub.ui.common.fmtDate(m.membership.startDate)} — ${com.tisbi.sportclub.ui.common.fmtDate(m.membership.endDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusChip(m.membership.status ?: "")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Выйти из учётной записи")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showPurchase) {
        PurchaseMembershipDialog(
            client = client,
            onDismiss = { showPurchase = false },
            onSuccess = { message ->
                showPurchase = false
                refresh++
                notify(message)
            },
            onError = { notify(it) }
        )
    }
}
