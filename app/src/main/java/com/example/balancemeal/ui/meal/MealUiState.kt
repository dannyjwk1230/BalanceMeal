package com.example.balancemeal.ui.meal

import com.example.balancemeal.model.*

sealed interface OperationState {
    data object Idle : OperationState
    data object Loading : OperationState
    data object Success : OperationState
    data class Error(val cause: Exception) : OperationState
}

data class MealUiState(
    val foods: List<Food> = emptyList(),
    val sourceInfo: SourceInfo? = null,
    val records: List<MealRecord> = emptyList(),
    /** 홈 조회 전용이며 식사 입력 날짜와 독립적입니다. */
    val homeDate: java.time.LocalDate? = null,
    val currentMeal: MealType = MealType.BREAKFAST,
    val draftsByMealType: Map<MealType, MealDraft> = emptyMap(),
    /** 행 오류의 키는 itemId, 초안 전체 오류는 items/records입니다. */
    val inputErrors: Map<String, String> = emptyMap(),
    val foodReadState: OperationState = OperationState.Idle,
    val recordReadState: OperationState = OperationState.Idle,
    val nutritionPreview: MealNutritionPreview = MealNutritionPreview(),
    val savingMealType: MealType? = null,
    val saveState: OperationState = OperationState.Idle
)
