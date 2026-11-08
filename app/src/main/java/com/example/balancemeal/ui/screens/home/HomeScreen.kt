package com.example.balancemeal.ui.screens.home

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.balancemeal.ui.components.ScreenPlaceholder
import com.example.balancemeal.ui.meal.*

@Composable
fun HomeScreen(state: MealUiState, onEvent: (MealEvent) -> Unit, onRecord: () -> Unit, onDataSources: () -> Unit) {
    ScreenPlaceholder("홈", listOf("오늘 요약", "이번 주 요약", "기록 화면 이동")) {
        MealFeedback(state, onEvent, records = true)
        Button(onClick = onRecord) { Text("기록으로 이동") }
        Button(onClick = onDataSources) { Text("데이터 출처") }
    }
}
