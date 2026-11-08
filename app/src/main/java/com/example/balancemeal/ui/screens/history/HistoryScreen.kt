package com.example.balancemeal.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.balancemeal.model.MealRecord
import com.example.balancemeal.ui.components.ScreenPlaceholder
import com.example.balancemeal.ui.meal.MealEvent
import com.example.balancemeal.ui.meal.MealFeedback
import com.example.balancemeal.ui.meal.MealUiState
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(state: MealUiState, onEvent: (MealEvent) -> Unit, onMealSelected: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        MealFeedback(state, onEvent, records = true)
        val totalKcal = state.records.sumOf { record ->
            record.items.sumOf { item ->
                item.food.nutrientsPer100.energyKcal * item.amount / 100.0
            }
        }.roundToInt()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "요약",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "총 열량: ${totalKcal} kcal")
                    Text(text = "끼니 수: ${state.records.size}끼")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(state.records, key = { it.recordId }) { record ->
                HistoryItemCard(record = record, onClick = { onMealSelected(record.recordId) })
            }
        }
    }
}

@Composable
fun HistoryItemCard(record: MealRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val photo = record.photoReferences.firstOrNull() ?: record.photoReference
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = "식사 사진",
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(80.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "사진 없음", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${record.date} ${record.time}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = record.mealType.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                if (record.photoReferences.size > 1) {
                    Text(
                        text = "추가 사진 ${record.photoReferences.size - 1}장",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun MealDetailPlaceholder() {
    ScreenPlaceholder("식사 상세", listOf("식사를 선택하면 상세가 표시됩니다"))
}
