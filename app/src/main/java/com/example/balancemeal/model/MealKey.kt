package com.example.balancemeal.model

import java.time.LocalDate

/** 날짜·끼니 조회 키. 시간은 식별자에 포함하지 않습니다. */
data class MealKey(val date: LocalDate, val mealType: MealType)
