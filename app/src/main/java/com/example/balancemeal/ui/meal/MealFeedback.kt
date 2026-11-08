package com.example.balancemeal.ui.meal

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun MealFeedback(
    state: MealUiState,
    onEvent: (MealEvent) -> Unit,
    food: Boolean = false,
    records: Boolean = false,
    save: Boolean = false
) {
    if (food) OperationFeedback("음식·출처 불러오기", state.foodReadState) {
        onEvent(MealEvent.RetryFoodReading)
    }
    if (records) OperationFeedback("기록 불러오기", state.recordReadState) {
        onEvent(MealEvent.RetryRecordReading)
    }
    if (save) {
        OperationFeedback("식사 저장", state.saveState) { onEvent(MealEvent.RetrySave) }
        state.inputErrors.values.distinct().forEach { Text(it) }
    }
}

@Composable
private fun OperationFeedback(label: String, state: OperationState, onRetry: () -> Unit) {
    when (state) {
        OperationState.Loading -> Text("$label 진행 중")
        is OperationState.Error -> {
            Text("$label 중 오류가 발생했습니다.")
            TextButton(onClick = onRetry) { Text("$label 다시 시도") }
        }
        OperationState.Idle, OperationState.Success -> Unit
    }
}
