package com.example.balancemeal.model

import com.example.balancemeal.model.serialization.LocalDateIsoSerializer
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** nutrition_source.json의 최상위 구조입니다. */
@Serializable
data class SourceInfo(
    val datasetName: String,
    val originalFileName: String,
    val sources: List<NutritionSource>
)

/** 개별 영양 정보 출처입니다. referenceDate는 ISO 날짜 문자열로 저장됩니다. */
@Serializable
data class NutritionSource(
    val sourceName: String,
    val providerName: String,
    @Serializable(with = LocalDateIsoSerializer::class)
    val referenceDate: LocalDate
)
