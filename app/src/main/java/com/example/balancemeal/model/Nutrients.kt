package com.example.balancemeal.model

import kotlinx.serialization.Serializable

@Serializable
data class Nutrients(
    val carbohydrateG: Double,
    val proteinG: Double,
    val fatG: Double,
    val energyKcal: Double
)
