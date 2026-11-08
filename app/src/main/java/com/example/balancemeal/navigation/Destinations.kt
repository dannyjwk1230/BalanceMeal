package com.example.balancemeal.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey
@Serializable data object Record : NavKey
@Serializable data object History : NavKey
@Serializable data object Analysis : NavKey
@Serializable data class FoodSelection(
    val mealType: com.example.balancemeal.model.MealType,
    val itemId: String
) : NavKey
@Serializable data class MealDetail(val recordId: String) : NavKey
@Serializable data object DataSources : NavKey
