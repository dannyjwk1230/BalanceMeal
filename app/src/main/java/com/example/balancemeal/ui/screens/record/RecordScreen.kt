package com.example.balancemeal.ui.screens.record

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.balancemeal.model.MealType
import com.example.balancemeal.ui.meal.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(state: MealUiState, onEvent: (MealEvent) -> Unit, onFoodSelection: (String) -> Unit) {
    val draft = state.draftsByMealType[state.currentMeal]
    val editable = state.savingMealType != state.currentMeal
    var showDate by rememberSaveable(state.currentMeal) { mutableStateOf(false) }
    var showTime by rememberSaveable(state.currentMeal) { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("기록", style = MaterialTheme.typography.headlineMedium)
        Text("날짜·시간·끼니", style = MaterialTheme.typography.titleMedium)
        MealType.entries.chunked(2).forEach { meals ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meals.forEach { meal ->
                    FilterChip(
                        selected = state.currentMeal == meal,
                        onClick = { onEvent(MealEvent.SelectMeal(meal)) },
                        label = { Text(meal.displayName) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (draft != null) {
            OutlinedButton(onClick = { showDate = true }, enabled = editable, modifier = Modifier.fillMaxWidth()) {
                Text("식사 날짜: ${draft.date}")
            }
            OutlinedButton(onClick = { showTime = true }, enabled = editable, modifier = Modifier.fillMaxWidth()) {
                Text("식사 시간: ${draft.time.format(DateTimeFormatter.ofPattern("HH:mm"))}")
            }
        }
        MealPhotoSection(
            mealType = state.currentMeal,
            photoReference = draft?.photoReference,
            enabled = editable,
            error = state.inputErrors["photo"],
            onPhotoSelected = { target, reference -> onEvent(MealEvent.SetPhoto(reference, target)) }
        )
        Text("음식 목록", style = MaterialTheme.typography.titleMedium)
        if (draft?.items.isNullOrEmpty()) Text("음식·반찬 추가 버튼으로 항목을 추가해 주세요.")
        draft?.items?.forEach { item ->
            key(item.itemId) {
                MealItemRow(
                    item = item,
                    enabled = editable,
                    error = state.inputErrors[item.itemId],
                    nutrients = state.nutritionPreview.byItemId[item.itemId],
                    onSelectFood = { onFoodSelection(item.itemId) },
                    onAmountChange = { onEvent(MealEvent.SetAmount(item.itemId, it)) },
                    onDelete = { onEvent(MealEvent.RemoveItem(item.itemId)) }
                )
            }
        }
        Button(
            onClick = { onEvent(MealEvent.AddItem) },
            enabled = draft != null && editable,
            modifier = Modifier.fillMaxWidth()
        ) { Text("음식·반찬 추가") }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("영양소 미리보기", style = MaterialTheme.typography.titleMedium)
                val total = state.nutritionPreview.total
                if (total != null) {
                    Text("식사 합계")
                    NutrientsDisplay(total)
                } else {
                    Text("모든 음식과 섭취량이 유효해야 식사 합계를 표시합니다.")
                }
            }
        }
        Button(
            onClick = { onEvent(MealEvent.SaveCurrentMeal) },
            enabled = state.foodReadState == OperationState.Success &&
                state.recordReadState == OperationState.Success && state.saveState != OperationState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.saveState == OperationState.Loading) "저장 중" else "식사 저장") }
        if (state.saveState == OperationState.Success) Text("식사를 저장했습니다.")
        state.inputErrors["items"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        MealFeedback(state, onEvent, food = true, records = true, save = true)
    }

    if (showDate && draft != null && editable) {
        // Material DatePicker의 millis는 UTC 날짜입니다. 앱 시간대로 다시 변환하지 않습니다.
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = draft.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis != null,
                    onClick = {
                        picker.selectedDateMillis?.let {
                            onEvent(MealEvent.SetDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))
                        }
                        showDate = false
                    }
                ) { Text("날짜 적용") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("취소") } }
        ) { DatePicker(state = picker) }
    }
    if (showTime && draft != null && editable) {
        val picker = rememberTimePickerState(
            initialHour = draft.time.hour, initialMinute = draft.time.minute, is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("식사 시간 선택") },
            text = { TimeInput(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    onEvent(MealEvent.SetTime(LocalTime.of(picker.hour, picker.minute)))
                    showTime = false
                }) { Text("시간 적용") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("취소") } }
        )
    }
}
