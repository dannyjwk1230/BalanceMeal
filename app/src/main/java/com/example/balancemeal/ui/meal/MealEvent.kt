package com.example.balancemeal.ui.meal

import com.example.balancemeal.model.MealType
import java.time.LocalDate
import java.time.LocalTime

sealed interface MealEvent {
    data object EnsureRecordInitialized : MealEvent
    data object EnterRecord : MealEvent
    data object RefreshTime : MealEvent
    data class MoveWeek(val weeks: Int) : MealEvent
    data class SelectMeal(val mealType: MealType) : MealEvent
    data class SetHomeDate(val date: LocalDate) : MealEvent
    data class SetDate(val date: LocalDate) : MealEvent
    data class SetTime(val time: LocalTime) : MealEvent
    data class SetPhoto(val reference: String?, val mealType: MealType, val date: LocalDate? = null) : MealEvent
    data class AddPhotos(val references: List<String>, val mealType: MealType, val date: LocalDate) : MealEvent
    data class RemovePhoto(val reference: String, val mealType: MealType, val date: LocalDate) : MealEvent
    data object AddItem : MealEvent
    data class SelectFood(val itemId: String, val foodCode: String, val mealType: MealType, val date: LocalDate? = null) : MealEvent
    data class SetAmount(val itemId: String, val input: String) : MealEvent
    data class RemoveItem(val itemId: String) : MealEvent
    data object SaveCurrentMeal : MealEvent
    data object DeleteCurrentMeal : MealEvent
    data object RetryFoodReading : MealEvent
    data object RetryRecordReading : MealEvent
    data object RetrySave : MealEvent
    data object ClearInputErrors : MealEvent
}
