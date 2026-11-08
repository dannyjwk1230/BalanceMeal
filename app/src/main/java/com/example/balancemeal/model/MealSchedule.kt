package com.example.balancemeal.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

fun weekMonday(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun weekTitle(monday: LocalDate): String {
    val thursday = weekMonday(monday).plusDays(3)
    return "${thursday.year}년 ${thursday.monthValue}월 ${(thursday.dayOfMonth - 1) / 7 + 1}째주"
}

fun defaultMealTime(type: MealType, entryTime: LocalTime): LocalTime = when (type) {
    MealType.BREAKFAST -> LocalTime.of(8, 0)
    MealType.LUNCH -> LocalTime.NOON
    MealType.DINNER -> LocalTime.of(18, 0)
    MealType.SNACK_DRINK -> entryTime
}

fun isFutureMeal(date: LocalDate, time: LocalTime, now: LocalDateTime): Boolean =
    date.atTime(time).isAfter(now)
