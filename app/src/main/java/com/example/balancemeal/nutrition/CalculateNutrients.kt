package com.example.balancemeal.nutrition

import com.example.balancemeal.model.Nutrients

/** G/ML 어느 쪽이든 해당 단위의 100 기준값을 사용합니다. 유효하지 않은 계산은 null입니다. */
fun calculateNutrients(nutrientsPer100: Nutrients, amount: Double): Nutrients? {
    if (!amount.isFinite() || amount <= 0.0 || !nutrientsPer100.isValidNutrition()) return null
    return Nutrients(
        carbohydrateG = nutrientsPer100.carbohydrateG * amount / 100.0,
        proteinG = nutrientsPer100.proteinG * amount / 100.0,
        fatG = nutrientsPer100.fatG * amount / 100.0,
        energyKcal = nutrientsPer100.energyKcal * amount / 100.0
    ).takeIf { it.isValidNutrition() }
}

fun Nutrients.isValidNutrition(): Boolean =
    listOf(carbohydrateG, proteinG, fatG, energyKcal).all { it.isFinite() && it >= 0.0 }
