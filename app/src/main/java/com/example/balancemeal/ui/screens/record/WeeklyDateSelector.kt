package com.example.balancemeal.ui.screens.record

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.balancemeal.model.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyDateSelector(monday: LocalDate, selected: LocalDate, today: LocalDate,
    onMove: (Int) -> Unit, onSelect: (LocalDate) -> Unit) {
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onMove(-1) }) { Text("<") }
            TextButton(onClick = { showCalendar = true }, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(weekTitle(monday), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            TextButton(onClick = { onMove(1) }, enabled = monday < weekMonday(today)) { Text(">") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("월", "화", "수", "목", "금", "토", "일").forEachIndexed { i, label ->
                val date = monday.plusDays(i.toLong())
                Surface(
                    onClick = { onSelect(date) }, enabled = date <= today,
                    color = if (date == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (date > today) 0.38f else 1f)
                        Text(label, fontSize = 12.sp, color = textColor)
                        Text("${date.monthValue}/${date.dayOfMonth}", fontSize = 12.sp, color = textColor)
                        Text(if (date == today) "오늘" else " ", fontSize = 10.sp, color = textColor)
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("편집 중인 날짜", style = MaterialTheme.typography.labelLarge)
                Text(
                    selected.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
    if (showCalendar) {
        // Material DatePicker는 날짜를 UTC 자정 밀리초로 표현합니다.
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialDisplayedMonthMillis = monday.plusDays(3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() <= today
                    } == true,
                    onClick = {
                        picker.selectedDateMillis?.let {
                            val date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            if (date <= today) onSelect(date)
                        }
                        showCalendar = false
                    }
                ) { Text("날짜 적용") }
            },
            dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("취소") } }
        ) {
            DatePicker(state = picker, title = { Text("식사 날짜 선택", Modifier.padding(24.dp)) })
        }
    }
}
