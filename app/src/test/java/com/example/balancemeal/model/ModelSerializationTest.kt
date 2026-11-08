package com.example.balancemeal.model

import com.example.balancemeal.model.serialization.LocalDateIsoSerializer
import com.example.balancemeal.model.serialization.LocalTimeIsoSerializer
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class ModelSerializationTest {
    @Test
    fun foodDecodesWithExactJsonFieldsAndUnits() {
        val food = Json.decodeFromString<Food>(
            """{"code":"001","name":"테스트 음식","unit":"ML","nutrientsPer100":{"carbohydrateG":1.5,"proteinG":2.0,"fatG":3.0,"energyKcal":41.0}}"""
        )
        assertEquals("001", food.code)
        assertEquals(AmountUnit.ML, food.unit)
        assertEquals(1.5, food.nutrientsPer100.carbohydrateG, 0.0)
        assertEquals(food, Json.decodeFromString<Food>(Json.encodeToString(food)))
    }

    @Test
    fun recordRoundTripPreservesIdsFoodSnapshotPhotoAndIsoDateTime() {
        val food = Food("001", "테스트 음식", AmountUnit.G, Nutrients(10.0, 5.0, 2.0, 78.0))
        val record = MealRecord(
            date = LocalDate.of(2024, 2, 29),
            time = LocalTime.of(12, 34, 56, 123456789),
            mealType = MealType.LUNCH,
            photoReference = "content://test/photo/1",
            items = listOf(MealItem(food = food, amount = 125.5))
        )
        val encoded = Json.encodeToString(record)
        val fields = Json.parseToJsonElement(encoded).jsonObject
        assertEquals("2024-02-29", fields.getValue("date").jsonPrimitive.content)
        assertEquals("12:34:56.123456789", fields.getValue("time").jsonPrimitive.content)
        assertEquals(
            setOf("recordId", "date", "time", "mealType", "photoReferences", "items"),
            fields.keys
        )
        assertEquals(record, Json.decodeFromString<MealRecord>(encoded))
        // Changing the catalog entry must not change the food captured in the record.
        val updatedFood = food.copy(name = "변경된 음식")
        assertNotEquals(updatedFood, record.items.single().food)
        assertEquals(food, record.items.single().food)
    }

    @Test
    fun incompleteDraftRoundTripPreservesRawAmountAndNulls() {
        val draft = MealDraft(
            date = LocalDate.of(2026, 11, 8),
            time = LocalTime.MIDNIGHT,
            items = listOf(
                MealItemDraft(amountInput = ""),
                MealItemDraft(amountInput = "0."),
                MealItemDraft(amountInput = "001.50")
            )
        )
        assertEquals(3, draft.items.map { it.itemId }.toSet().size)
        val restored = Json.decodeFromString<MealDraft>(Json.encodeToString(draft))
        assertEquals(draft, restored)
        assertNull(restored.photoReference)
        assertTrue(restored.items.all { it.food == null })
    }

    @Test
    fun isoSerializersRejectInvalidValuesAndAcceptMinutePrecision() {
        assertEquals(
            LocalTime.of(9, 30),
            Json.decodeFromString(LocalTimeIsoSerializer, "\"" + "09:30" + "\"")
        )
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString(LocalDateIsoSerializer, "\"" + "2026-02-30" + "\"")
        }
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString(LocalTimeIsoSerializer, "\"" + "24:00:00" + "\"")
        }
    }
}
