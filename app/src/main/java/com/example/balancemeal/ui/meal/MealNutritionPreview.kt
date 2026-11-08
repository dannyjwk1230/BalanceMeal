package com.example.balancemeal.ui.meal

import com.example.balancemeal.model.Nutrients

/** 저장하지 않는 현재 초안의 계산 결과입니다. total은 모든 행이 유효할 때만 존재합니다. */
data class MealNutritionPreview(
    val byItemId: Map<String, Nutrients> = emptyMap(),
    val total: Nutrients? = null
)
