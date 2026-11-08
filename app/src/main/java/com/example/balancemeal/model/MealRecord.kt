package com.example.balancemeal.model

import com.example.balancemeal.model.serialization.LocalDateIsoSerializer
import com.example.balancemeal.model.serialization.LocalTimeIsoSerializer
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.serialization.Serializable

/** 합계 영양소는 items로 계산하며 별도로 저장하지 않습니다. */
@Serializable
data class MealRecord(
    val recordId: String = UUID.randomUUID().toString(),
    @Serializable(with = LocalDateIsoSerializer::class)
    val date: LocalDate,
    @Serializable(with = LocalTimeIsoSerializer::class)
    val time: LocalTime,
    val mealType: MealType,
    val photoReference: String? = null,
    val items: List<MealItem>
)
