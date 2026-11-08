package com.example.balancemeal.ui.screens.history

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.balancemeal.ui.components.ScreenPlaceholder
import com.example.balancemeal.ui.meal.*

@Composable
fun HistoryScreen(state: MealUiState, onEvent: (MealEvent) -> Unit, onMealSelected: (String) -> Unit) {
    ScreenPlaceholder("내역", listOf("날짜별 요약", "식사 목록")) {
        MealFeedback(state, onEvent, records = true)
    }
}

@Composable
fun MealDetailPlaceholder() {
    ScreenPlaceholder("식사 상세", listOf("식사를 선택하면 상세가 표시됩니다"))
}
