package com.example.balancemeal.ui.meal

import com.example.balancemeal.model.MealType
import java.time.LocalDate

/**
 * 넓은 화면의 오른쪽 패널에서 음식을 선택할 때 사용하는 요청 정보입니다.
 * [itemId]로 추가·변경 대상 행을 안정적으로 구분합니다.
 * [date]는 선택기를 열 당시의 날짜입니다.
 * null이면 패널이 비활성 상태입니다.
 */
data class FoodPickerRequest(
    val itemId: String,
    val mealType: MealType,
    val date: LocalDate
)
