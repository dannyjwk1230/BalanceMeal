package com.example.balancemeal.model

data class MealAnalysis(
    val totalKcal30Days: Double = 0.0,
    val nutrientsByMealType: Map<MealType, Nutrients> = emptyMap()
)
