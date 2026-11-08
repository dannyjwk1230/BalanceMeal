package com.example.balancemeal.ui.screens.detail

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.balancemeal.ui.components.ScreenPlaceholder
import com.example.balancemeal.ui.meal.*

@Composable
fun MealDetailScreen(recordId: String, state: MealUiState, onEvent: (MealEvent) -> Unit) {
    ScreenPlaceholder("식사 상세", listOf("식사 정보", "사진", "음식 목록", "영양소")) {
        MealFeedback(state, onEvent, records = true)
    }
}
