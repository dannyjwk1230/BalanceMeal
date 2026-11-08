package com.example.balancemeal.model

import java.util.UUID
import kotlinx.serialization.Serializable

/** food는 저장 당시의 전체 스냅샷이며, amount는 해당 음식의 G 또는 ML 단위입니다. */
@Serializable
data class MealItem(
    val itemId: String = UUID.randomUUID().toString(),
    val food: Food,
    val amount: Double
)
