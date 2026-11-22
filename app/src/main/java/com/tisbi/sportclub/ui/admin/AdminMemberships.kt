package com.tisbi.sportclub.ui.admin

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tisbi.sportclub.db.MembershipDetails
import com.tisbi.sportclub.db.MembershipStatus
import com.tisbi.sportclub.db.PaymentMethod
import com.tisbi.sportclub.repo.Database
import com.tisbi.sportclub.repo.OpResult
import com.tisbi.sportclub.ui.common.ConfirmDialog
import com.tisbi.sportclub.ui.common.DropdownField
import com.tisbi.sportclub.ui.common.EmptyState
import com.tisbi.sportclub.ui.common.SearchField
import com.tisbi.sportclub.ui.common.StatusChip
import com.tisbi.sportclub.ui.common.fmtDate
import com.tisbi.sportclub.ui.common.money

/** Подсистема работы с абонементами (ТЗ п. 4.2.2) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MembershipsAdminTab(notify: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var renewing by remember { mutableStateOf<MembershipDetails?>(null) }
    var freezing by remember { mutableStateOf<MembershipDetails?>(null) }
    var deleting by remember { mutableStateOf<MembershipDetails?>(null) }
    var clientPicker by remember { mutableStateOf(false) }
    var purchaseFor by remember { mutableStateOf<com.tisbi.sportclub.db.Client?>(null) }

    val memberships = remember(refresh, search, statusFilter) {
        Database.memberships.all(search.ifBlank { null })
            .filter { statusFilter == null || it.membership.status == statusFilter }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SearchField(search, { search = it }, hint = "Поиск по клиенту, типу или статусу")
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("Все") })
            listOf(
                MembershipStatus.ACTIVE, MembershipStatus.FROZEN,
                MembershipStatus.INACTIVE, MembershipStatus.CLOSED
            ).forEach { s ->
                FilterChip(
                    selected = statusFilter == s,
                    onClick = { statusFilter = if (statusFilter == s) null else s },
                    label = { Text(s) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (memberships.isEmpty()) {
            EmptyState("Абонементы не найдены")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)
            ) {
                items(memberships, key = { it.membership.id }) { m ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.clientFio, fontWeight = FontWeight.Medium)
                                    Text(
                                        m.typeName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${fmtDate(m.membership.startDate)} — ${fmtDate(m.membership.endDate)} • ${money(m.membership.purchasePrice)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusChip(m.membership.status ?: "")
                            }
                            Row {
                                IconButton(
                                    onClick = { renewing = m },
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Autorenew, contentDescription = "Продлить",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { freezing = m },
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AcUnit, contentDescription = "Заморозить",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (m.membership.status != MembershipStatus.CLOSED) {
                                    IconButton(
                                        onClick = {
                                            Database.memberships.setStatus(m.membership.id, MembershipStatus.CLOSED)
                                            refresh++
                                            notify("Абонемент закрыт")
                                        },
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = "Закрыть")
                                    }
                                }
                                IconButton(onClick = { deleting = m }) {
                                    Icon(
                                        Icons.Default.Delete, contentDescription = "Удалить",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(end = 20.dp, bottom = 30.dp), Alignment.BottomEnd) {
        ExtendedFloatingActionButton(
            onClick = { clientPicker = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Оформить абонемент") }
        )
    }

    if (clientPicker) {
        ClientPickerDialog(
            onDismiss = { clientPicker = false },
            onPicked = { client ->
                clientPicker = false
                purchaseFor = client
            }
        )
    }

    purchaseFor?.let { client ->
        PurchaseForClientDialog(
            client = client,
            onDismiss = { purchaseFor = null },
            onSuccess = { message ->
                purchaseFor = null
                refresh++
                notify(message)
            }
        )
    }

    renewing?.let { m ->
        val priceText = remember(m.membership.id) {
            com.tisbi.sportclub.repo.TypesRepo()
                .membershipTypeById(m.membership.idMembershipType)?.price?.let { money(it) } ?: "—"
        }
        PaymentMethodDialog(
            title = "Продление абонемента",
            text = "Клиент: ${m.clientFio}\nТип: ${m.typeName} (${m.duration} дн.)\nСтоимость: $priceText",
            onConfirm = { method ->
                when (val res = Database.memberships.renew(m.membership.id, method)) {
                    is OpResult.Ok -> { refresh++; notify("Абонемент продлён, чек сформирован") }
                    is OpResult.Error -> notify(res.message)
                }
                renewing = null
            },
            onDismiss = { renewing = null }
        )
    }

    freezing?.let { m ->
        var days by remember { mutableStateOf("14") }
        AlertDialog(
            onDismissRequest = { freezing = null },
            title = { Text("Заморозка абонемента") },
            text = {
                Column {
                    Text("Клиент: ${m.clientFio}\nДата окончания будет увеличена на срок заморозки.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = days,
                        onValueChange = { days = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Срок заморозки, дней") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (val res = Database.memberships.freeze(m.membership.id, days.toIntOrNull() ?: 0)) {
                        is OpResult.Ok -> { refresh++; notify("Абонемент заморожен") }
                        is OpResult.Error -> notify(res.message)
                    }
                    freezing = null
                }) { Text("Заморозить") }
            },
            dismissButton = { TextButton(onClick = { freezing = null }) { Text("Отмена") } }
        )
    }

    deleting?.let { m ->
        ConfirmDialog(
            title = "Удаление абонемента",
            text = "Вы действительно хотите удалить абонемент «${m.typeName}» клиента ${m.clientFio}? Связанные платежи будут удалены.",
            onConfirm = {
                Database.memberships.delete(m.membership.id)
                deleting = null
                refresh++
                notify("Абонемент удалён")
            },
            onDismiss = { deleting = null }
        )
    }
}

/** Диалог выбора клиента (ТЗ п. 4.2: поле «Клиент» с таблицей и поиском) */
@Composable
private fun ClientPickerDialog(
    onDismiss: () -> Unit,
    onPicked: (com.tisbi.sportclub.db.Client) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val clients = remember(search) { Database.clients.all(search.ifBlank { null }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбор клиента") },
        text = {
            Column {
                SearchField(search, { search = it }, hint = "Поиск по ФИО")
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(clients, key = { it.id }) { c ->
                        Card(
                            onClick = { onPicked(c) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(c.fioFull, fontWeight = FontWeight.Medium)
                                Text(
                                    "Карта ${c.cardNumber ?: "—"} • ${c.phone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Выбор способа оплаты */
@Composable
internal fun PaymentMethodDialog(
    title: String,
    text: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text)
                Spacer(Modifier.height(10.dp))
                DropdownField(
                    label = "Способ оплаты",
                    options = listOf(PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.ONLINE),
                    selected = method,
                    optionLabel = { it },
                    onSelect = { method = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(method) }) { Text("Подтвердить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
