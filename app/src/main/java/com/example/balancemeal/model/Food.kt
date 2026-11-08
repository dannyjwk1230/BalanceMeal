package com.example.balancemeal.model

import kotlinx.serialization.Serializable

/** nutrientsPer100은 unit에 따라 100g 또는 100mL 기준입니다. */
@Serializable
data class Food(
    val code: String,
    val name: String,
    val unit: AmountUnit,
    val nutrientsPer100: Nutrients
)
