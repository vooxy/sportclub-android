package com.tisbi.sportclub.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tisbi.sportclub.db.Client
import com.tisbi.sportclub.db.User
import com.tisbi.sportclub.repo.Database
import com.tisbi.sportclub.repo.MembershipRepo
import com.tisbi.sportclub.repo.OpResult
import com.tisbi.sportclub.ui.common.ConfirmDialog
import com.tisbi.sportclub.ui.common.DropdownField
import com.tisbi.sportclub.ui.common.EmptyState
import com.tisbi.sportclub.ui.common.SearchField
import com.tisbi.sportclub.ui.common.StatusChip
import com.tisbi.sportclub.ui.common.fmtDate
import com.tisbi.sportclub.ui.common.fmtDateHuman
import com.tisbi.sportclub.ui.common.fmtDateTimeHuman
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Интерфейс администратора клуба */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(user: User, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    val tabs = listOf(
        "Клиенты" to Icons.Default.Person,
        "Тренеры" to Icons.Default.SportsMartialArts,
        "Занятия" to Icons.Default.CalendarMonth,
        "Абонементы" to Icons.Default.CreditCard,
        "Ещё" to Icons.Default.Menu
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Спортивный клуб — администратор") },
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
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.second, contentDescription = null) },
                        label = { Text(t.first, maxLines = 1) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> ClientsTab(::notify)
                1 -> TrainersTab(::notify)
                2 -> SessionsTab(::notify)
                3 -> MembershipsAdminTab(::notify)
                else -> MoreTab(::notify, onLogout)
            }
        }
    }
}

// ----------------------------------------------------------------------
// Модуль «Управление клиентами» (ТЗ п. 4.2.1.1)
// ----------------------------------------------------------------------

@Composable
internal fun ClientsTab(notify: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Client?>(null) }
    var creating by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Client?>(null) }
    var deleting by remember { mutableStateOf<Client?>(null) }

    val clients = remember(refresh, search) { Database.clients.all(search.ifBlank { null }) }
    val repo = remember { MembershipRepo() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SearchField(search, { search = it }, hint = "Поиск по фамилии, телефону или карте")
        Spacer(Modifier.height(12.dp))
        if (clients.isEmpty()) {
            EmptyState("Клиенты не найдены")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)
            ) {
                items(clients, key = { it.id }) { c ->
                    val active = remember(refresh, c.id) { repo.activeMembership(c.id) }
                    Card(
                        onClick = { opened = c },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.fioFull, fontWeight = FontWeight.Medium)
                                    Text(
                                        "Карта ${c.cardNumber ?: "—"} • ${c.phone}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (active != null) StatusChip("Активен до ${fmtDate(active.endDate)}")
                                else StatusChip("Без абонемента")
                            }
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(end = 20.dp, bottom = 30.dp), Alignment.BottomEnd) {
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Создать учётную запись") }
        )
    }

    if (creating) {
        ClientFormDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { client ->
                creating = false
                refresh++
                notify("Клиент «${client.fio}» зарегистрирован. Номер карты: ${client.cardNumber}")
            },
            onError = { notify(it) }
        )
    }

    editing?.let { edit ->
        ClientFormDialog(
            initial = edit,
            onDismiss = { editing = null },
            onSave = { client ->
                editing = null
                refresh++
                notify("Данные клиента «${client.fio}» обновлены")
            },
            onError = { notify(it) }
        )
    }

    opened?.let { c ->
        ClientCardDialog(
            client = Database.clients.byId(c.id) ?: c,
            onEdit = { editing = it; opened = null },
            onDelete = { deleting = it; opened = null },
            onClose = { opened = null },
            onMembershipEvent = { refresh++ }
        )
    }

    deleting?.let { c ->
        ConfirmDialog(
            title = "Удаление клиента",
            text = "Вы действительно хотите удалить клиента «${c.fio}»? Будут удалены его абонементы, записи и платежи.",
            onConfirm = {
                Database.memberships.forClient(c.id).forEach { m ->
                    Database.memberships.delete(m.membership.id)
                }
                Database.write { db ->
                    db.delete("Attendance", "ID_Client = ?", arrayOf(c.id.toString()))
                    db.delete("Users", "ID_Client = ?", arrayOf(c.id.toString()))
                    db.delete("Clients", "ID = ?", arrayOf(c.id.toString()))
                }
                deleting = null
                refresh++
                notify("Клиент удалён")
            },
            onDismiss = { deleting = null }
        )
    }
}

/**
 * Форма создания/редактирования клиента с валидацией по ТЗ п. 4.2:
 * ФИО — только буквы, до 30 символов, первая заглавная; телефон +7 (11 цифр);
 * e-mail — проверка формата; дата рождения — не позже текущей.
 */
@Composable
internal fun ClientFormDialog(
    initial: Client?,
    onDismiss: () -> Unit,
    onSave: (Client) -> Unit,
    onError: (String) -> Unit
) {
    var lastName by remember { mutableStateOf(initial?.lastName ?: "") }
    var firstName by remember { mutableStateOf(initial?.firstName ?: "") }
    var middleName by remember { mutableStateOf(initial?.middleName ?: "") }
    var gender by remember { mutableStateOf(initial?.gender ?: "м") }
    var birthDate by remember { mutableStateOf(initial?.birthDate?.let { LocalDate.parse(it) }) }
    var phone by remember { mutableStateOf(initial?.phone ?: "+7 (") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val titleText = if (initial == null) {
                "Новая учётная запись клиента"
            } else {
                "Редактирование клиента"
            }
            Text(titleText)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { value -> lastName = value.take(30) },
                    label = { Text("Фамилия *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { value -> firstName = value.take(30) },
                    label = { Text("Имя *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = middleName,
                    onValueChange = { value -> middleName = value.take(30) },
                    label = { Text("Отчество") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = gender == "м",
                        onClick = { gender = "м" },
                        label = { Text("мужской") }
                    )
                    FilterChip(
                        selected = gender == "ж",
                        onClick = { gender = "ж" },
                        label = { Text("женский") }
                    )
                }
                PhoneField(phone, { value -> phone = value })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { value -> email = value.take(150) },
                    label = { Text("Электронная почта *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                com.tisbi.sportclub.ui.common.DatePickerField(
                    label = "Дата рождения *",
                    value = birthDate,
                    onChange = { birthDate = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val err = validateClientForm(
                        lastName, firstName, middleName, phone, email, birthDate
                    )
                    if (err != null) {
                        error = err
                    } else {
                        val card = initial?.cardNumber ?: Database.clients.nextCardNumber()
                        val client = Client(
                            id = initial?.id ?: 0,
                            lastName = lastName.trim(),
                            firstName = firstName.trim(),
                            middleName = middleName.trim().ifBlank { null },
                            gender = gender,
                            birthDate = birthDate.toString(),
                            phone = phone,
                            email = email.trim().ifBlank { null },
                            cardNumber = card,
                            registrationDate = initial?.registrationDate ?: LocalDate.now().toString()
                        )
                        if (initial == null) {
                            val id = Database.clients.insert(client)
                            // Автоматическое создание учётной записи: логин client+ID, пароль — 4 цифры телефона
                            val login = "client$id"
                            val digits = phone.filter { ch -> ch.isDigit() }
                            val pwd = if (digits.length >= 4) digits.takeLast(4) else "0000"
                            Database.write { db ->
                                Database.users.createClientAccount(
                                    db, id, login, pwd, client.lastName + " " + client.firstName
                                )
                            }
                            onSave(client.copy(id = id))
                        } else {
                            Database.clients.update(client)
                            onSave(client)
                        }
                    }
                }
            ) {
                val buttonText = if (initial == null) "Создать" else "Подтвердить"
                Text(buttonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

/** Проверка данных клиента по ТЗ п. 4.2; возвращает текст ошибки или null */
private fun validateClientForm(
    lastName: String,
    firstName: String,
    middleName: String,
    phone: String,
    email: String,
    birthDate: LocalDate?
): String? {
    val nameRegex = Regex("^[А-ЯЁA-Z][а-яёa-z-]{1,29}$")
    if (lastName.isBlank()) return "Заполните фамилию"
    if (!nameRegex.matches(lastName)) return "Фамилия: только буквы, до 30 символов, первая заглавная"
    if (firstName.isBlank()) return "Заполните имя"
    if (!nameRegex.matches(firstName)) return "Имя: только буквы, до 30 символов, первая заглавная"
    if (middleName.isNotBlank() && !nameRegex.matches(middleName)) {
        return "Отчество: только буквы, до 30 символов, первая заглавная"
    }
    val digits = phone.filter { ch -> ch.isDigit() }
    if (digits.length != 11) return "Номер должен содержать 11 цифр"
    if (email.isNotBlank()) {
        val emailRegex = Regex("^[\\w.+-]+@[\\w-]+\\.[\\w.]+$")
        if (!emailRegex.matches(email)) return "Неверный или некорректный e-mail"
    }
    if (birthDate == null) return "Укажите дату рождения"
    if (birthDate.isAfter(LocalDate.now())) return "Дата рождения не может быть больше текущей даты"
    return null
}

/** Поле телефона с маской +7 (XXX) XXX-XX-XX */
@Composable
private fun PhoneField(value: String, onChange: (String) -> Unit) {
    fun mask(input: String): String {
        val digits = input.filter { it.isDigit() }
            .let { if (it.startsWith("8")) "7" + it.drop(1) else it }
            .take(11)
            .let { if (it.isEmpty()) "" else if (it[0] != '7') "7$it" else it }
        if (digits.length <= 1) return "+7 ("
        val sb = StringBuilder("+7 (")
        sb.append(digits.substring(1, minOf(4, digits.length)))
        if (digits.length >= 4) sb.append(") ")
        if (digits.length >= 7) {
            sb.append(digits.substring(4, 7))
            sb.append("-")
        } else if (digits.length > 4) sb.append(digits.substring(4))
        if (digits.length >= 9) {
            sb.append(digits.substring(7, 9))
            sb.append("-")
        } else if (digits.length > 7) sb.append(digits.substring(7))
        if (digits.length >= 11) sb.append(digits.substring(9, 11))
        else if (digits.length > 9) sb.append(digits.substring(9))
        return sb.toString()
    }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(mask(it)) },
        label = { Text("Телефон *") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Карточка клиента: данные, история абонементов и посещений (ТЗ п. 4.2.1.1) */
@Composable
private fun ClientCardDialog(
    client: Client,
    onEdit: (Client) -> Unit,
    onDelete: (Client) -> Unit,
    onClose: () -> Unit,
    onMembershipEvent: () -> Unit
) {
    var showPurchase by remember { mutableStateOf(false) }
    val memberships = remember(client) { Database.memberships.forClient(client.id) }
    val visits = remember(client) { Database.attendance.forClient(client.id) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(client.fioFull) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                com.tisbi.sportclub.ui.common.KeyValueRow("Номер карты", client.cardNumber ?: "—")
                com.tisbi.sportclub.ui.common.KeyValueRow("Пол", if (client.gender == "ж") "женский" else "мужской")
                com.tisbi.sportclub.ui.common.KeyValueRow("Дата рождения", fmtDateHuman(client.birthDate))
                com.tisbi.sportclub.ui.common.KeyValueRow("Телефон", client.phone)
                com.tisbi.sportclub.ui.common.KeyValueRow("Почта", client.email ?: "—")
                com.tisbi.sportclub.ui.common.KeyValueRow("Дата регистрации", fmtDate(client.registrationDate))

                Text(
                    "Абонементы (${memberships.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                memberships.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.typeName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(
                                "${fmtDate(m.membership.startDate)} — ${fmtDate(m.membership.endDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusChip(m.membership.status ?: "")
                    }
                }

                Text(
                    "Посещения (${visits.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                visits.take(8).forEach { v ->
                    Text(
                        "• ${if (v.sessionName.isNullOrBlank()) "Свободное посещение" else v.sessionName} — ${fmtDateTimeHuman(v.attendance.visitDateTime)} (${v.attendance.status})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onEdit(client) }) { Text("Изменить") }
                TextButton(onClick = { showPurchase = true }) { Text("Абонемент") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(client) }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onClose) { Text("Закрыть") }
            }
        }
    )

    if (showPurchase) {
        PurchaseForClientDialog(
            client = client,
            onDismiss = { showPurchase = false },
            onSuccess = { showPurchase = false; onMembershipEvent() }
        )
    }
}

/** Оформление абонемента администратором (ТЗ п. 4.2.2) */
@Composable
internal fun PurchaseForClientDialog(
    client: Client,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val types = remember { com.tisbi.sportclub.repo.TypesRepo().membershipTypes() }
    var typeId by remember { mutableStateOf<Long?>(null) }
    var method by remember { mutableStateOf(com.tisbi.sportclub.db.PaymentMethod.CASH) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Оформление абонемента") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(client.fioFull, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                DropdownField(
                    label = "Тип абонемента",
                    options = types,
                    selected = types.firstOrNull { it.id == typeId },
                    optionLabel = { "${it.typeName} (${it.duration} дн., ${com.tisbi.sportclub.ui.common.money(it.price)})" },
                    onSelect = { typeId = it.id; error = null }
                )
                Spacer(Modifier.height(10.dp))
                DropdownField(
                    label = "Способ оплаты",
                    options = listOf(
                        com.tisbi.sportclub.db.PaymentMethod.CASH,
                        com.tisbi.sportclub.db.PaymentMethod.CARD,
                        com.tisbi.sportclub.db.PaymentMethod.ONLINE
                    ),
                    selected = method,
                    optionLabel = { it },
                    onSelect = { method = it }
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (typeId == null) {
                    error = "Выберите тип абонемента"
                } else {
                    when (val res = Database.memberships.purchase(client.id, typeId!!, method)) {
                        is OpResult.Ok -> onSuccess("Абонемент оформлен, чек сформирован")
                        is OpResult.Error -> error = res.message
                    }
                }
            }) { Text("Оформить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
