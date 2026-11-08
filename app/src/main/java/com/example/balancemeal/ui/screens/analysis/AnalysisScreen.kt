package com.example.balancemeal.ui.screens.analysis

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.balancemeal.ui.components.ScreenPlaceholder
import com.example.balancemeal.ui.meal.*

@Composable
fun AnalysisScreen(state: MealUiState, onEvent: (MealEvent) -> Unit) {
    ScreenPlaceholder("분석", listOf("최근 1개월 기간", "총열량", "끼니별 분석")) {
        MealFeedback(state, onEvent, records = true)
    }
}
