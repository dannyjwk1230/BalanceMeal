package com.example.balancemeal.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.balancemeal.data.FoodRepository
import com.example.balancemeal.data.MealRepository
import java.time.Clock

class MealViewModelFactory(
    private val foodRepository: FoodRepository,
    private val mealRepository: MealRepository,
    private val clock: Clock,
    private val photoStorage: com.example.balancemeal.data.PhotoStorage
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MealViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return MealViewModel(foodRepository, mealRepository, clock, photoStorage) as T
    }
}
