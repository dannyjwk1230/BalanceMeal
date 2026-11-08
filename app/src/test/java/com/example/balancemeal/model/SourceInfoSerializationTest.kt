package com.example.balancemeal.model

import java.time.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceInfoSerializationTest {
    @Test
    fun providedSourceJsonRoundTripPreservesFieldsAndIsoDates() {
        // 제공된 nutrition_source.json의 구조와 값을 고정한 계약 테스트입니다.
        val sourceJson = """
            {
              "datasetName": "전국통합식품영양성분정보_음식_표준데이터",
              "originalFileName": "전국통합식품영양성분정보_음식_표준데이터-20261108.xls",
              "sources": [
                {
                  "sourceName": "농촌진흥청(국가표준식품성분표)",
                  "providerName": "식품의약품안전처",
                  "referenceDate": "2026-09-29"
                },
                {
                  "sourceName": "식품의약품안전처",
                  "providerName": "식품의약품안전처",
                  "referenceDate": "2026-09-29"
                }
              ]
            }
        """.trimIndent()

        val info = Json.decodeFromString<SourceInfo>(sourceJson)
        assertEquals("전국통합식품영양성분정보_음식_표준데이터", info.datasetName)
        assertEquals(2, info.sources.size)
        assertEquals(LocalDate.of(2026, 9, 29), info.sources.first().referenceDate)
        assertEquals(
            Json.parseToJsonElement(sourceJson),
            Json.parseToJsonElement(Json.encodeToString(info))
        )
        assertEquals(info, Json.decodeFromString<SourceInfo>(Json.encodeToString(info)))
    }
}
