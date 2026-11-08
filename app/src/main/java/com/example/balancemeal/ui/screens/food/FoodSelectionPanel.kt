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

/**
 * 넓은 화면의 오른쪽 패널에 삽입되는 음식 검색·선택 Composable입니다.
 * [FoodSelectionScreen]과 같은 로직을 사용하지만 뒤로 가기 버튼 대신
 * 취소([onCancel]) 버튼을 제공하고, 패널에 맞게 독립 스크롤합니다.
 */
@Composable
fun FoodSelectionPanel(
    state: MealUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    targetAvailable: Boolean,
    onSelectFood: (String) -> Unit,
    onCancel: () -> Unit,
    onEvent: (MealEvent) -> Unit
) {
    val results = state.foods.filter { it.name.contains(query.trim(), ignoreCase = true) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("음식 선택", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onCancel) { Text("취소") }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("음식 검색") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("food-search-panel")
        )

        MealFeedback(state, onEvent, food = true)

        if (!targetAvailable) {
            Text("선택할 음식 항목이 삭제되었습니다. 왼쪽 목록에서 항목을 추가해 주세요.")
        }
        if (results.isEmpty() && state.foodReadState == OperationState.Success) {
            Text("검색 결과가 없습니다.")
        }

        // 결과 목록만 독립적으로 스크롤 (패널 헤더·검색창은 고정)
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("food-results-panel")
        ) {
            items(results, key = { it.code }) { food ->
                OutlinedCard(
                    onClick = { onSelectFood(food.code) },
                    enabled = targetAvailable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("food-panel-${food.code}")
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(food.name, style = MaterialTheme.typography.titleMedium)
                        Text(if (food.unit == AmountUnit.G) "100g 기준" else "100ml 기준")
                    }
                }
            }
        }
    }
}
