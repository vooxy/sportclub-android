package com.tisbi.sportclub.ui.admin

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tisbi.sportclub.db.MembershipType
import com.tisbi.sportclub.repo.Database
import com.tisbi.sportclub.repo.OpResult
import com.tisbi.sportclub.repo.TypesRepo
import com.tisbi.sportclub.ui.common.ConfirmDialog
import com.tisbi.sportclub.ui.common.EmptyState
import com.tisbi.sportclub.ui.common.StatusChip
import com.tisbi.sportclub.ui.common.fmtDateTimeHuman
import com.tisbi.sportclub.ui.common.money
import java.time.LocalDate

/** Раздел «Ещё»: оплаты, контроль доступа, справочники типов */
@Composable
internal fun MoreTab(notify: (String) -> Unit, onLogout: () -> Unit) {
    var screen by remember { mutableStateOf("menu") }

    when (screen) {
        "payments" -> PaymentsAdminScreen({ screen = "menu" }, notify)
        "turnstile" -> TurnstileScreen({ screen = "menu" }, notify)
        "types" -> TypesScreen({ screen = "menu" }, notify)
        else -> MoreMenu(screen = { screen = it }, onLogout = onLogout)
    }
}

@Composable
private fun MoreMenu(screen: (String) -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        MenuTile(
            icon = Icons.Default.Payments, title = "Оплаты и платежи",
            subtitle = "Приём платежей, журнал операций"
        ) { screen("payments") }
        MenuTile(
            icon = Icons.Default.Timer, title = "Контроль доступа (турникет)",
            subtitle = "Регистрация прохода по клиентской карте, журнал"
        ) { screen("turnstile") }
        MenuTile(
            icon = Icons.Default.Sports, title = "Типы абонементов и занятий",
            subtitle = "Справочники: виды абонементов, виды занятий"
        ) { screen("types") }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Logout, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Выйти из учётной записи")
        }
    }
}

@Composable
private fun MenuTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// Журнал платежей администратора
// ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentsAdminScreen(onBack: () -> Unit, notify: (String) -> Unit) {
    var period by remember { mutableIntStateOf(1) } // 0 все, 1 день, 2 неделя, 3 месяц
    var refresh by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<Long?>(null) }

    val from = when (period) {
        1 -> LocalDate.now()
        2 -> LocalDate.now().minusDays(7)
        3 -> LocalDate.now().minusMonths(1)
        else -> null
    }
    val payments = remember(refresh, period) { Database.payments.all(from = from) }
    val total = payments.filter { it.payment.status == com.tisbi.sportclub.db.PaymentStatus.PAID }
        .sumOf { it.payment.amount ?: 0.0 }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text(
                "Оплаты",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = period == 0, onClick = { period = 0 }, label = { Text("Все") })
            FilterChip(selected = period == 1, onClick = { period = 1 }, label = { Text("Сегодня") })
            FilterChip(selected = period == 2, onClick = { period = 2 }, label = { Text("Неделя") })
            FilterChip(selected = period == 3, onClick = { period = 3 }, label = { Text("Месяц") })
        }
        Card(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Поступления за период",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    money(total),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        if (payments.isEmpty()) {
            EmptyState("Платежи не найдены")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
            ) {
                items(payments, key = { it.payment.id }) { p ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.clientFio, fontWeight = FontWeight.Medium)
                                Text(
                                    "${p.membershipTypeName.ifBlank { "—" }} • ${fmtDateTimeHuman(p.payment.paymentDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${p.payment.paymentMethod} • ${money(p.payment.amount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusChip(p.payment.status ?: "")
                            IconButton(onClick = { deleting = p.payment.id }) {
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

    deleting?.let { id ->
        ConfirmDialog(
            title = "Удаление платежа",
            text = "Удалить запись о платеже из базы данных?",
            onConfirm = {
                Database.payments.delete(id)
                deleting = null
                refresh++
                notify("Платёж удалён")
            },
            onDismiss = { deleting = null }
        )
    }
}

// ----------------------------------------------------------------------
// Контроль доступа: регистрация прохода через турникет (ТЗ п. 4.2.5)
// ----------------------------------------------------------------------

@Composable
private fun TurnstileScreen(onBack: () -> Unit, notify: (String) -> Unit) {
    var card by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val log = remember(refresh) { Database.attendance.accessLog() }
    val cards = remember { Database.clients.all().mapNotNull { it.cardNumber } }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text(
                "Контроль доступа",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Text(
            "Приложите клиентскую карту к RFID-считывателю (введите номер карты)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = card,
                onValueChange = { card = it.uppercase() },
                label = { Text("Номер карты") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = {
                    result = when (val res = Database.attendance.registerGymEntry(card)) {
                        is OpResult.Ok -> true to "Проход разрешён. Посещение зарегистрировано."
                        is OpResult.Error -> false to res.message
                    }
                    refresh++
                },
                modifier = Modifier.height(56.dp)
            ) { Text("Приложить") }
        }

        // Подсказка: существующие карты
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            cards.take(3).forEach { c ->
                FilterChip(selected = card == c, onClick = { card = c }, label = { Text(c) })
            }
        }

        result?.let { (ok, message) ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (ok) Color(0xFFDCF5E3) else Color(0xFFFFE0E0)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = if (ok) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        message,
                        color = if (ok) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Text(
            "Журнал проходов",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
        )
        if (log.isEmpty()) {
            EmptyState("Проходов пока не было")
        } else {
            log.forEach { entry ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.clientFio, fontWeight = FontWeight.Medium)
                            Text(
                                fmtDateTimeHuman(entry.attendance.visitDateTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusChip(entry.attendance.status)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Справочники: типы абонементов и типы занятий
// ----------------------------------------------------------------------

@Composable
private fun TypesScreen(onBack: () -> Unit, notify: (String) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val typesRepo = remember { TypesRepo() }
    val membershipTypes = remember(refresh) { typesRepo.membershipTypes() }
    val sessionTypes = remember(refresh) { typesRepo.sessionTypes() }
    var editingMembershipType by remember { mutableStateOf<MembershipType?>(null) }
    var creatingMembershipType by remember { mutableStateOf(false) }
    var newSessionType by remember { mutableStateOf("") }
    var newSessionPrice by remember { mutableStateOf("") }
    var deletingSessionType by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text(
                "Справочники",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Типы абонементов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { creatingMembershipType = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text(" Добавить")
            }
        }
        membershipTypes.forEach { t ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.typeName, fontWeight = FontWeight.Medium)
                        Text(
                            "${t.duration} дн. • ${money(t.price)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { editingMembershipType = t }) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                    }
                    IconButton(onClick = {
                        when (val res = typesRepo.deleteMembershipType(t.id)) {
                            is OpResult.Ok -> { refresh++; notify("Тип абонемента удалён") }
                            is OpResult.Error -> notify(res.message)
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Text(
            "Типы занятий",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )
        sessionTypes.forEach { t ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${t.typeName}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(
                        t.price?.let { money(it) } ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { deletingSessionType = t.id }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newSessionType,
                onValueChange = { newSessionType = it },
                label = { Text("Новый тип занятия") },
                singleLine = true,
                modifier = Modifier.weight(1.4f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = newSessionPrice,
                onValueChange = { newSessionPrice = it.filter { c -> c.isDigit() } },
                label = { Text("Цена") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (newSessionType.isBlank()) {
                    notify("Укажите название типа занятия")
                } else {
                    typesRepo.insertSessionType(newSessionType.trim(), newSessionPrice.toDoubleOrNull() ?: 0.0)
                    newSessionType = ""
                    newSessionPrice = ""
                    refresh++
                    notify("Тип занятия добавлен")
                }
            }) { Text("Добавить") }
        }
    }

    creatingMembershipType.let {
        if (it) {
            MembershipTypeFormDialog(
                initial = null,
                onDismiss = { creatingMembershipType = false },
                onSave = { t ->
                    typesRepo.insertMembershipType(t)
                    creatingMembershipType = false
                    refresh++
                    notify("Тип абонемента добавлен")
                }
            )
        }
    }

    editingMembershipType?.let { t ->
        MembershipTypeFormDialog(
            initial = t,
            onDismiss = { editingMembershipType = null },
            onSave = { updated ->
                typesRepo.updateMembershipType(updated)
                editingMembershipType = null
                refresh++
                notify("Тип абонемента обновлён")
            }
        )
    }

    deletingSessionType?.let { id ->
        ConfirmDialog(
            title = "Удаление типа занятия",
            text = "Удалить этот тип занятия?",
            onConfirm = {
                when (val res = typesRepo.deleteSessionType(id)) {
                    is OpResult.Ok -> { refresh++; notify("Тип занятия удалён") }
                    is OpResult.Error -> notify(res.message)
                }
                deletingSessionType = null
            },
            onDismiss = { deletingSessionType = null }
        )
    }
}

@Composable
private fun MembershipTypeFormDialog(
    initial: MembershipType?,
    onDismiss: () -> Unit,
    onSave: (MembershipType) -> Unit
) {
    var name by remember { mutableStateOf(initial?.typeName ?: "") }
    var duration by remember { mutableStateOf(initial?.duration?.toString() ?: "30") }
    var price by remember { mutableStateOf(initial?.price?.toInt()?.toString() ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый тип абонемента" else "Редактирование типа") },
        text = {
            Column {
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Продолжительность, дней *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text("Цена, ₽ *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.isBlank() -> error = "Укажите название"
                    duration.toIntOrNull() == null || duration.toInt() <= 0 -> error = "Укажите длительность"
                    price.isBlank() -> error = "Укажите цену"
                    else -> onSave(
                        MembershipType(
                            id = initial?.id ?: 0,
                            typeName = name.trim(),
                            duration = duration.toInt(),
                            price = price.toDouble(),
                            description = description.trim().ifBlank { null }
                        )
                    )
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
