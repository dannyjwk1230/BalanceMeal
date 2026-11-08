package com.example.balancemeal

import android.content.Context
import com.example.balancemeal.data.AssetFoodRepository
import com.example.balancemeal.data.FoodRepository
import com.example.balancemeal.data.LocalMealRepository
import com.example.balancemeal.data.MealRepository
import com.example.balancemeal.ui.meal.MealViewModelFactory
import java.io.File
import java.time.Clock

/** 앱 전체에서 같은 저장소와 Clock을 사용합니다. 초기 읽기와 오류 상태는 MealViewModel이 관리합니다. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val foodRepository: FoodRepository = AssetFoodRepository(appContext.assets)
    val mealRepository: MealRepository =
        LocalMealRepository(File(appContext.filesDir, "meal_records.json"))
    val clock: Clock = Clock.systemDefaultZone()
    val photoStorage: com.example.balancemeal.data.PhotoStorage =
        com.example.balancemeal.data.FilePhotoStorage(
            File(appContext.filesDir, "images"), appContext.contentResolver
        )
    val mealViewModelFactory = MealViewModelFactory(foodRepository, mealRepository, clock, photoStorage)
}
