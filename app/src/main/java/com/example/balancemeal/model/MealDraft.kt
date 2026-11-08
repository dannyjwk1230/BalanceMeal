package com.example.balancemeal.model

import com.example.balancemeal.model.serialization.LocalDateIsoSerializer
import com.example.balancemeal.model.serialization.LocalTimeIsoSerializer
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class MealDraft(
    @Serializable(with = LocalDateIsoSerializer::class)
    val date: LocalDate,
    @Serializable(with = LocalTimeIsoSerializer::class)
    val time: LocalTime,
    val photoReference: String? = null,
    val items: List<MealItemDraft> = emptyList()
)
