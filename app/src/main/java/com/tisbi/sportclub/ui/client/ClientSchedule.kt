package com.tisbi.sportclub.ui.client

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tisbi.sportclub.db.Client
import com.tisbi.sportclub.db.MembershipStatus
import com.tisbi.sportclub.db.PaymentMethod
import com.tisbi.sportclub.db.Roles
import com.tisbi.sportclub.db.SessionDetails
import com.tisbi.sportclub.repo.Database
import com.tisbi.sportclub.repo.MembershipRepo
import com.tisbi.sportclub.repo.OpResult
import com.tisbi.sportclub.repo.TypesRepo
import com.tisbi.sportclub.ui.common.StatusChip
import com.tisbi.sportclub.ui.common.fmtDateTimeHuman
import com.tisbi.sportclub.ui.common.money

/**
 * Расписание занятий с фильтрами и записью (ТЗ п. 4.1.1, 4.2.3).
 * Запись выполняется только при действующем абонементе.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTab(
    client: Client?,
    onEvent: (message: String, reload: Boolean) -> Unit
) {
    val typesRepo = remember { TypesRepo() }
    val membershipRepo = remember { MembershipRepo() }
    var refresh by remember { mutableStateOf(0) }

    var typeFilter by remember { mutableStateOf<Long?>(null) }
    var selected by remember { mutableStateOf<SessionDetails?>(null) }

    val sessionTypes = remember { typesRepo.sessionTypes() }
    val sessions = remember(refresh, typeFilter) {
        Database.schedule.sessions(typeId = typeFilter)
    }
    val activeMembership = remember(refresh, client) {
        client?.let { membershipRepo.activeMembership(it.id) }
    }
    val myBookings = remember(refresh, client) {
        client?.let { Database.attendance.forClient(it.id, upcomingOnly = true) } ?: emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        // Фильтры по типу занятия
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = typeFilter == null,
                onClick = { typeFilter = null },
                label = { Text("Все типы") }
            )
            sessionTypes.forEach { st ->
                FilterChip(
                    selected = typeFilter == st.id,
                    onClick = { typeFilter = st.id },
                    label = { Text(st.typeName) }
                )
            }
        }

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Занятий не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sessions, key = { it.session.id }) { s ->
                    SessionCard(
                        details = s,
                        isBooked = myBookings.any { it.attendance.idSession == s.session.id },
                        hasMembership = activeMembership != null,
                        onOpen = { selected = s }
                    )
                }
            }
        }
    }

    // Детали занятия и запись
    selected?.let { sel ->
        SessionDetailsDialog(
            details = sel,
            isBooked = myBookings.any { it.attendance.idSession == sel.session.id },
            hasMembership = activeMembership != null,
            onBook = {
                val result = Database.attendance.book(client!!.id, sel.session.id)
                when (result) {
                    is OpResult.Ok -> {
                        refresh++
                        onEvent("Вы записаны на «${sel.session.sessionName}». Талон доступен в разделе «Записи».", true)
                    }
                    is OpResult.Error -> onEvent(result.message, false)
                }
                selected = null
            },
            onDismiss = { selected = null }
        )
    }
}

@Composable
private fun SessionCard(
    details: SessionDetails,
    isBooked: Boolean,
    hasMembership: Boolean,
    onOpen: () -> Unit
) {
    val s = details.session
    val full = details.bookedCount >= s.maxParticipants
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    s.sessionName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (isBooked) {
                    Icon(
                        Icons.Default.Check, contentDescription = "Записан",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccessTime, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    fmtDateTimeHuman(s.startDateTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${details.typeName} • ${details.trainerFio}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Place, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        s.hall ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.EventSeat, null,
                        modifier = Modifier.size(14.dp),
                        tint = if (full) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Места: ${details.bookedCount} / ${s.maxParticipants}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (full) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (full) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Детали занятия: полная информация и кнопка записи */
@Composable
private fun SessionDetailsDialog(
    details: SessionDetails,
    isBooked: Boolean,
    hasMembership: Boolean,
    onBook: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = details.session
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.sessionName) },
        text = {
            Column {
                com.tisbi.sportclub.ui.common.KeyValueRow("Тип занятия", details.typeName)
                com.tisbi.sportclub.ui.common.KeyValueRow("Дата и время", fmtDateTimeHuman(s.startDateTime))
                com.tisbi.sportclub.ui.common.KeyValueRow("Длительность", "${s.duration} мин")
                com.tisbi.sportclub.ui.common.KeyValueRow("Тренер", details.trainerFio)
                com.tisbi.sportclub.ui.common.KeyValueRow("Зал", s.hall ?: "—")
                com.tisbi.sportclub.ui.common.KeyValueRow(
                    "Свободные места",
                    "${s.maxParticipants - details.bookedCount} из ${s.maxParticipants}"
                )
            }
        },
        confirmButton = {
            when {
                isBooked -> TextButton(onClick = onDismiss) { Text("Вы уже записаны") }
                !hasMembership -> TextButton(onClick = onDismiss) {
                    Text("Нужен действующий абонемент", color = MaterialTheme.colorScheme.error)
                }
                else -> TextButton(onClick = onBook) { Text("Записаться") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

/**
 * Покупка/продление абонемента клиентом (ТЗ п. 4.2.4 — онлайн-оплата).
 * После выбора типа абонемента выбирается способ оплаты, формируется
 * запись в таблицах Memberships и Payments.
 */
@Composable
fun PurchaseMembershipDialog(
    client: Client,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val typesRepo = remember { TypesRepo() }
    val membershipRepo = remember { MembershipRepo() }
    val types = remember { typesRepo.membershipTypes() }
    var chosen by remember { mutableStateOf<Long?>(null) }
    var paying by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Покупка абонемента",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    client.fioFull,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(types, key = { it.id }) { t ->
                        val selected = chosen == t.id
                        Card(
                            onClick = { chosen = t.id },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.typeName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${t.duration} дн. • ${money(t.price)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (!t.description.isNullOrBlank()) {
                                        Text(
                                            t.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (!paying) {
                    Button(
                        onClick = { chosen?.let { paying = true } },
                        enabled = chosen != null,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Перейти к оплате")
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Отмена") }
                } else {
                    val type = types.firstOrNull { it.id == chosen }
                    Text(
                        "Оплата: ${type?.typeName} — ${money(type?.price)}",
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(10.dp))
                    listOf(
                        PaymentMethod.ONLINE,
                        PaymentMethod.CARD,
                        PaymentMethod.CASH
                    ).forEach { method ->
                        OutlinedButton(
                            onClick = {
                                val result = membershipRepo.purchase(client.id, chosen!!, method)
                                when (result) {
                                    is OpResult.Ok ->
                                        onSuccess("Абонемент «${type?.typeName}» оплачен и активирован. Чек сформирован.")
                                    is OpResult.Error -> onError(result.message)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(method) }
                    }
                    TextButton(onClick = { paying = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("← Назад к выбору абонемента")
                    }
                }
            }
        }
    }
}
