package com.example.balancemeal.ui.screens.sources

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.balancemeal.ui.components.ScreenPlaceholder
import com.example.balancemeal.ui.meal.*

@Composable
fun DataSourcesScreen(state: MealUiState, onEvent: (MealEvent) -> Unit, onBack: () -> Unit) {
    ScreenPlaceholder("데이터 출처", listOf("공공데이터 출처", "영양 정보 기준")) {
        MealFeedback(state, onEvent, food = true)
        TextButton(onClick = onBack) { Text("돌아가기") }
    }
}
