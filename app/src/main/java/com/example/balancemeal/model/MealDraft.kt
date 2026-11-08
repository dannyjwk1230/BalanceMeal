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
    val photoReferences: List<String> = emptyList(),
    val items: List<MealItemDraft> = emptyList()
) {
    /** 이전 단일 사진 호출부를 위한 읽기 전용 호환 접근자입니다. */
    val photoReference: String? get() = photoReferences.firstOrNull()

    constructor(date: LocalDate, time: LocalTime, photoReference: String?, items: List<MealItemDraft> = emptyList()) :
        this(date, time, listOfNotNull(photoReference), items)
}
