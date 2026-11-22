package com.tisbi.sportclub.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tisbi.sportclub.db.AttendanceStatus
import com.tisbi.sportclub.db.MembershipStatus
import com.tisbi.sportclub.db.PaymentStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ----------------------------------------------------------------------
// Форматирование дат и сумм
// ----------------------------------------------------------------------

private val MONTHS_GEN = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря"
)
private val WEEKDAYS_SHORT = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

fun parseIsoDate(s: String?): LocalDate? =
    s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun parseIsoDateTime(s: String?): LocalDateTime? =
    s?.let { runCatching { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrNull() }

/** «12.05.2004» */
fun fmtDate(s: String?): String = parseIsoDate(s)?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "—"

/** «12 мая 2026» */
fun fmtDateHuman(s: String?): String {
    val d = parseIsoDate(s) ?: return "—"
    return "${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]} ${d.year}"
}

/** «ср, 12 мая, 10:00» */
fun fmtDateTimeHuman(s: String?): String {
    val d = parseIsoDateTime(s) ?: return "—"
    return "${WEEKDAYS_SHORT[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]}, %02d:%02d".format(d.hour, d.minute)
}

/** «10:00» */
fun fmtTime(s: String?): String {
    val d = parseIsoDateTime(s) ?: return "—"
    return "%02d:%02d".format(d.hour, d.minute)
}

fun money(v: Double?): String {
    val n = v ?: 0.0
    val whole = n.toLong().toString()
    val grouped = StringBuilder()
    var count = 0
    for (i in whole.indices.reversed()) {
        grouped.insert(0, whole[i])
        count++
        if (count % 3 == 0 && i > 0) grouped.insert(0, ' ')
    }
    return "$grouped ₽"
}

/** Склонение: plural(5, "день", "дня", "дней") */
fun plural(n: Long, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1L -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

// ----------------------------------------------------------------------
// Переиспользуемые элементы интерфейса
// ----------------------------------------------------------------------

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String = "Поиск...") {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

/** Цветной статус-бейдж */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (text) {
        MembershipStatus.ACTIVE -> Color(0xFFDCF5E3) to Color(0xFF1B5E20)
        MembershipStatus.FROZEN -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
        MembershipStatus.INACTIVE, MembershipStatus.CLOSED -> Color(0xFFEEEEEE) to Color(0xFF616161)
        PaymentStatus.PAID -> Color(0xFFDCF5E3) to Color(0xFF1B5E20)
        PaymentStatus.PENDING -> Color(0xFFFFF3C4) to Color(0xFF8A6D00)
        PaymentStatus.REFUNDED -> Color(0xFFFFE0E0) to Color(0xFFB71C1C)
        AttendanceStatus.BOOKED -> Color(0xFFE8EAF6) to Color(0xFF1A237E)
        AttendanceStatus.VISITED -> Color(0xFFDCF5E3) to Color(0xFF1B5E20)
        AttendanceStatus.CANCELLED -> Color(0xFFEEEEEE) to Color(0xFF757575)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Удалить",
    danger: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

/** Поле с подписью и значением внутри карточки */
@Composable
fun InfoLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
