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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.tisbi.sportclub.db.Trainer
import com.tisbi.sportclub.repo.Database
import com.tisbi.sportclub.repo.OpResult
import com.tisbi.sportclub.ui.common.ConfirmDialog
import com.tisbi.sportclub.ui.common.EmptyState
import com.tisbi.sportclub.ui.common.SearchField
import com.tisbi.sportclub.ui.common.fmtDate
import java.time.LocalDate

/** Модуль «Управление тренерами» (ТЗ п. 4.2.1.2) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainersTab(notify: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Trainer?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Trainer?>(null) }

    val trainers = remember(refresh, search) { Database.trainers.all(search.ifBlank { null }) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SearchField(search, { search = it }, hint = "Поиск по фамилии или специализации")
        Spacer(Modifier.height(12.dp))
        if (trainers.isEmpty()) {
            EmptyState("Тренеры не найдены")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)
            ) {
                items(trainers, key = { it.id }) { t ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(t.fio, fontWeight = FontWeight.Medium)
                                Text(
                                    "${t.specialization ?: "—"} • ${t.phone ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Принят: ${fmtDate(t.hireDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editing = t }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Редактировать"
                                )
                            }
                            IconButton(onClick = { deleting = t }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error
                                )
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
            text = { Text("Добавить тренера") }
        )
    }

    if (creating) {
        TrainerFormDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { creating = false; refresh++; notify("Тренер «${it.fio}» добавлен") },
            onError = { notify(it) }
        )
    }

    editing?.let { t ->
        TrainerFormDialog(
            initial = t,
            onDismiss = { editing = null },
            onSave = { editing = null; refresh++; notify("Данные тренера обновлены") },
            onError = { notify(it) }
        )
    }

    deleting?.let { t ->
        ConfirmDialog(
            title = "Удаление тренера",
            text = "Вы действительно хотите удалить тренера «${t.fio}»?",
            onConfirm = {
                when (val res = Database.trainers.delete(t.id)) {
                    is OpResult.Ok -> { refresh++; notify("Тренер удалён") }
                    is OpResult.Error -> notify(res.message)
                }
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
internal fun TrainerFormDialog(
    initial: Trainer?,
    onDismiss: () -> Unit,
    onSave: (Trainer) -> Unit,
    onError: (String) -> Unit
) {
    var lastName by remember { mutableStateOf(initial?.lastName ?: "") }
    var firstName by remember { mutableStateOf(initial?.firstName ?: "") }
    var middleName by remember { mutableStateOf(initial?.middleName ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "+7 (") }
    var specialization by remember { mutableStateOf(initial?.specialization ?: "") }
    var hireDate by remember { mutableStateOf(initial?.hireDate?.let { LocalDate.parse(it) }) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый тренер" else "Редактирование тренера") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (error != null) {
                    Text(
                        error!!, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = lastName, onValueChange = { if (it.length <= 30) lastName = it },
                    label = { Text("Фамилия *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                )
                OutlinedTextField(
                    value = firstName, onValueChange = { if (it.length <= 30) firstName = it },
                    label = { Text("Имя *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                )
                OutlinedTextField(
                    value = middleName, onValueChange = { if (it.length <= 30) middleName = it },
                    label = { Text("Отчество") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Телефон") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                )
                OutlinedTextField(
                    value = specialization, onValueChange = { specialization = it },
                    label = { Text("Специализация *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                )
                com.tisbi.sportclub.ui.common.DatePickerField(
                    label = "Дата приёма на работу",
                    value = hireDate,
                    onChange = { hireDate = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val nameRegex = Regex("^[А-ЯЁA-Z][а-яёa-z-]{1,29}\$")
                when {
                    lastName.isBlank() -> error = "Заполните фамилию"
                    !nameRegex.matches(lastName) -> error = "Фамилия: только буквы, первая заглавная"
                    firstName.isBlank() -> error = "Заполните имя"
                    !nameRegex.matches(firstName) -> error = "Имя: только буквы, первая заглавная"
                    specialization.isBlank() -> error = "Укажите специализацию"
                    phone.filter { it.isDigit() }.length !in 0..11 -> error = "Некорректный телефон"
                    hireDate == null -> error = "Укажите дату приёма"
                    else -> {
                        val trainer = Trainer(
                            id = initial?.id ?: 0,
                            lastName = lastName.trim(),
                            firstName = firstName.trim(),
                            middleName = middleName.trim().ifBlank { null },
                            phone = phone.ifBlank { null },
                            specialization = specialization.trim(),
                            hireDate = hireDate.toString()
                        )
                        if (initial == null) onSave(trainer.copy(id = Database.trainers.insert(trainer)))
                        else { Database.trainers.update(trainer); onSave(trainer) }
                    }
                }
            }) { Text(if (initial == null) "Добавить" else "Подтвердить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
