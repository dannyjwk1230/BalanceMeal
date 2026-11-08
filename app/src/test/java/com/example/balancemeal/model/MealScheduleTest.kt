package com.example.balancemeal.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class MealScheduleTest {
    @Test fun weekIsContinuousAcrossMonthAndYearAndNamedByThursday() {
        val cases = mapOf(
            "2026-06-01" to "2026년 6월 1째주",
            "2026-06-29" to "2026년 7월 1째주",
            "2025-12-29" to "2026년 1월 1째주",
            "2026-12-28" to "2026년 12월 5째주",
            "2027-01-04" to "2027년 1월 1째주"
        )
        cases.forEach { (date, title) ->
            val start = LocalDate.parse(date)
            assertEquals(title, weekTitle(start))
            repeat(7) { assertEquals(start, weekMonday(start.plusDays(it.toLong()))) }
            assertEquals(start.plusDays(6), (0L..6L).map(start::plusDays).last())
        }
    }

    @Test fun defaultsAndFutureValidationRespectRealDates() {
        assertEquals(LocalTime.of(8, 0), defaultMealTime(MealType.BREAKFAST, LocalTime.of(7, 0)))
        assertEquals(LocalTime.NOON, defaultMealTime(MealType.LUNCH, LocalTime.MIN))
        assertEquals(LocalTime.of(18, 0), defaultMealTime(MealType.DINNER, LocalTime.MIN))
        assertEquals(LocalTime.of(7, 35), defaultMealTime(MealType.SNACK_DRINK, LocalTime.of(7, 35)))
        val now = LocalDateTime.of(2026, 6, 1, 7, 0)
        assertTrue(isFutureMeal(now.toLocalDate(), LocalTime.of(8, 0), now))
        assertFalse(isFutureMeal(now.toLocalDate().minusDays(1), LocalTime.MAX, now))
        assertFalse(isFutureMeal(now.toLocalDate(), LocalTime.of(7, 0), now))
    }
}
