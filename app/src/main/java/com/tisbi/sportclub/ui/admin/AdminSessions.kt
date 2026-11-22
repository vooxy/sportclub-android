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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.tisbi.sportclub.db.WorkoutSession
import com.tisbi.sportclub.repo.Database
import com.tisbi.sportclub.repo.OpResult
import com.tisbi.sportclub.repo.TypesRepo
import com.tisbi.sportclub.ui.common.ConfirmDialog
import com.tisbi.sportclub.ui.common.DropdownField
import com.tisbi.sportclub.ui.common.EmptyState
import com.tisbi.sportclub.ui.common.TimePickerField
import com.tisbi.sportclub.ui.common.DatePickerField
import com.tisbi.sportclub.ui.common.fmtDateTimeHuman
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HALLS = listOf("Зал №1", "Зал №2", "Зал единоборств", "Бассейн")

/** Подсистема работы с расписанием занятий (ТЗ п. 4.2.3) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionsTab(notify: (String) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var hallFilter by remember { mutableStateOf<String?>(null) }
    var showPast by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkoutSession?>(null) }
    var deleting by remember { mutableStateOf<WorkoutSession?>(null) }

    val sessions = remember(refresh, hallFilter, showPast) {
        Database.schedule.sessions(hall = hallFilter, includePast = showPast)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = hallFilter == null,
                onClick = { hallFilter = null },
                label = { Text("Все залы") }
            )
            HALLS.forEach { hall ->
                FilterChip(
                    selected = hallFilter == hall,
                    onClick = { hallFilter = hall },
                    label = { Text(hall) }
                )
            }
            FilterChip(
                selected = showPast,
                onClick = { showPast = !showPast },
                label = { Text("Прошедшие") }
            )
        }
        Spacer(Modifier.height(12.dp))
        if (sessions.isEmpty()) {
            EmptyState("Занятий не найдено")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)
            ) {
                items(sessions, key = { it.session.id }) { s ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (showPast) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.session.sessionName, fontWeight = FontWeight.Medium)
                                Text(
                                    "${fmtDateTimeHuman(s.session.startDateTime)} • ${s.session.hall}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${s.typeName} • ${s.trainerFio} • ${s.bookedCount}/${s.session.maxParticipants} мест",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editing = s.session }) {
                                Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                            }
                            IconButton(onClick = { deleting = s.session }) {
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

    Box(Modifier.fillMaxSize().padding(end = 20.dp, bottom = 30.dp), Alignment.BottomEnd) {
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Добавить занятие") }
        )
    }

    if (creating) {
        SessionFormDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { result ->
                creating = false
                refresh++
                notify(
                    when (result) {
                        is OpResult.Ok -> "Занятие добавлено в расписание"
                        is OpResult.Error -> result.message
                    }
                )
            }
        )
    }

    editing?.let { s ->
        SessionFormDialog(
            initial = s,
            onDismiss = { editing = null },
            onSave = { result ->
                editing = null
                refresh++
                notify(
                    when (result) {
                        is OpResult.Ok -> "Занятие обновлено"
                        is OpResult.Error -> result.message
                    }
                )
            }
        )
    }

    deleting?.let { s ->
        val booked = remember(s.id) {
            Database.attendance.forSession(s.id).count { it.attendance.status == com.tisbi.sportclub.db.AttendanceStatus.BOOKED }
        }
        ConfirmDialog(
            title = "Удаление занятия",
            text = if (booked > 0)
                "Удалить «${s.sessionName}»? На него записано клиентов: $booked. Их записи будут отменены."
            else
                "Вы действительно хотите удалить занятие «${s.sessionName}»?",
            onConfirm = {
                Database.schedule.delete(s.id)
                deleting = null
                refresh++
                notify("Занятие удалено из расписания")
            },
            onDismiss = { deleting = null }
        )
    }
}

/**
 * Форма занятия: название, тип, тренер, дата, время, длительность,
 * лимит участников, зал. Перед сохранением выполняется проверка
 * пересечений по времени для тренера и зала (ТЗ п. 4.2.3).
 */
@Composable
internal fun SessionFormDialog(
    initial: WorkoutSession?,
    onDismiss: () -> Unit,
    onSave: (OpResult) -> Unit
) {
    val types = remember { TypesRepo().sessionTypes() }
    val trainers = remember { Database.trainers.all() }

    val initialDate = initial?.startDateTime?.let {
        runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull()
    }
    val initialTime = initial?.startDateTime?.let {
        runCatching { LocalTime.parse(it.substring(11, 16)) }.getOrNull()
    }

    var name by remember { mutableStateOf(initial?.sessionName ?: "") }
    var typeId by remember { mutableStateOf(initial?.idSessionType ?: types.firstOrNull()?.id) }
    var trainerId by remember { mutableStateOf(initial?.idTrainer ?: trainers.firstOrNull()?.id) }
    var date by remember { mutableStateOf(initialDate ?: LocalDate.now().plusDays(1)) }
    var time by remember { mutableStateOf(initialTime ?: LocalTime.of(10, 0)) }
    var duration by remember { mutableStateOf((initial?.duration ?: 60).toString()) }
    var maxParticipants by remember { mutableStateOf((initial?.maxParticipants ?: 12).toString()) }
    var hall by remember { mutableStateOf(initial?.hall ?: HALLS.first()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новое занятие" else "Редактирование занятия") },
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
                    value = name, onValueChange = { name = it },
                    label = { Text("Название занятия *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                )
                DropdownField(
                    label = "Тип занятия",
                    options = types,
                    selected = types.firstOrNull { it.id == typeId },
                    optionLabel = { it.typeName },
                    onSelect = { typeId = it.id }
                )
                DropdownField(
                    label = "Тренер",
                    options = trainers,
                    selected = trainers.firstOrNull { it.id == trainerId },
                    optionLabel = { "${it.fio} (${it.specialization ?: "—"})" },
                    onSelect = { trainerId = it.id }
                )
                DropdownField(
                    label = "Зал",
                    options = HALLS,
                    selected = hall,
                    optionLabel = { it },
                    onSelect = { hall = it }
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        DatePickerField(label = "Дата", value = date, onChange = { date = it })
                    }
                    Box(Modifier.weight(1f)) {
                        TimePickerField(label = "Время", value = time, onChange = { time = it })
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Длительность, мин") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxParticipants,
                        onValueChange = { maxParticipants = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Макс. участников") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.isBlank() -> error = "Укажите название занятия"
                    duration.toIntOrNull() == null || duration.toInt() <= 0 -> error = "Укажите длительность в минутах"
                    maxParticipants.toIntOrNull() == null || maxParticipants.toInt() <= 0 -> error = "Укажите лимит участников"
                    trainerId == null -> error = "Выберите тренера"
                    else -> {
                        val start = date.atTime(time).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        val session = WorkoutSession(
                            id = initial?.id ?: 0,
                            sessionName = name.trim(),
                            idSessionType = typeId,
                            idTrainer = trainerId,
                            startDateTime = start,
                            duration = duration.toInt(),
                            maxParticipants = maxParticipants.toInt(),
                            hall = hall
                        )
                        val result = if (initial == null) Database.schedule.insert(session, trainerId!!)
                        else Database.schedule.update(session, trainerId!!)
                        onSave(result)
                    }
                }
            }) { Text(if (initial == null) "Добавить" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
