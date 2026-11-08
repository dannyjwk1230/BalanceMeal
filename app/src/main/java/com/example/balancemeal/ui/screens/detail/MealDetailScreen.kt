package com.example.balancemeal.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.balancemeal.ui.components.MealPhoto
import com.example.balancemeal.ui.meal.*

@Composable
fun MealDetailScreen(recordId: String, state: MealUiState, onEvent: (MealEvent) -> Unit) {
    val record = state.records.find { it.recordId == recordId }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("식사 상세", style = MaterialTheme.typography.headlineMedium) }
        item { MealFeedback(state, onEvent, records = true) }
        if (record == null) {
            item { Text("식사 기록을 찾을 수 없습니다.") }
        } else {
            item { Text("${record.date} · ${record.mealType.displayName} · ${record.time}") }
            item { Text("사진", style = MaterialTheme.typography.titleMedium) }
            if (record.photoReferences.isEmpty()) item { Text("사진 없음") }
            itemsIndexed(record.photoReferences) { index, reference ->
                Column {
                    Text("사진 ${index + 1} / ${record.photoReferences.size}")
                    MealPhoto(reference)
                }
            }
            item { Text("음식 목록", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(record.items) { _, food ->
                Text("${food.food.name} · ${food.amount} ${food.food.unit.name.lowercase()}")
            }
            item { Text("영양소", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
