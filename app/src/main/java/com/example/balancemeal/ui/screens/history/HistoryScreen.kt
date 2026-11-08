package com.example.balancemeal.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.balancemeal.ui.components.MealPhoto
import com.example.balancemeal.ui.components.ScreenPlaceholder
import com.example.balancemeal.ui.meal.*

@Composable
fun HistoryScreen(state: MealUiState, onEvent: (MealEvent) -> Unit, onMealSelected: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("내역", style = MaterialTheme.typography.headlineMedium) }
        item { Text("날짜별 요약", style = MaterialTheme.typography.titleMedium) }
        item { MealFeedback(state, onEvent, records = true) }
        item { Text("식사 목록", style = MaterialTheme.typography.titleMedium) }
        if (state.records.isEmpty()) item { Text("저장된 식사가 없습니다.") }
        items(state.records.sortedWith(compareByDescending<com.example.balancemeal.model.MealRecord> { it.date }
            .thenByDescending { it.time }), key = { it.recordId }) { record ->
            OutlinedCard(onClick = { onMealSelected(record.recordId) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${record.date} · ${record.mealType.displayName} · ${record.time}")
                    record.photoReferences.firstOrNull()?.let { MealPhoto(it) } ?: Text("사진 없음")
                    if (record.photoReferences.size > 1) Text("추가 사진 ${record.photoReferences.size - 1}장")
                }
            }
        }
    }
}

@Composable
fun MealDetailPlaceholder() {
    ScreenPlaceholder("식사 상세", listOf("식사를 선택하면 상세가 표시됩니다"))
}
