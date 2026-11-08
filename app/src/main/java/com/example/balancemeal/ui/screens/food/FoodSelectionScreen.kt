package com.example.balancemeal.ui.screens.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.balancemeal.model.AmountUnit
import com.example.balancemeal.ui.meal.*

@Composable
fun FoodSelectionScreen(
    state: MealUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    targetAvailable: Boolean,
    onSelectFood: (String) -> Unit,
    onEvent: (MealEvent) -> Unit,
    onBack: () -> Unit
) {
    val results = state.foods.filter { it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("음식 선택", style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = onBack) { Text("돌아가기") }
        OutlinedTextField(
            value = query, onValueChange = onQueryChange,
            label = { Text("음식 검색") },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("food-search")
        )
        MealFeedback(state, onEvent, food = true)
        if (!targetAvailable) Text("선택할 음식 항목이 삭제되었습니다. 돌아가서 항목을 추가해 주세요.")
        if (results.isEmpty() && state.foodReadState == OperationState.Success) {
            Text("검색 결과가 없습니다.")
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("food-results")) {
            items(results, key = { it.code }) { food ->
                OutlinedCard(
                    onClick = { onSelectFood(food.code) },
                    enabled = targetAvailable,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("food-${food.code}")
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(food.name, style = MaterialTheme.typography.titleMedium)
                        Text(if (food.unit == AmountUnit.G) "100g 기준" else "100ml 기준")
                    }
                }
            }
        }
    }
}
