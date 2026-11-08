package com.example.balancemeal.model

import kotlinx.serialization.Serializable

@Serializable
enum class MealType(val displayName: String) {
    BREAKFAST("조식"),
    LUNCH("중식"),
    DINNER("석식"),
    SNACK_DRINK("간식/음료")
}
