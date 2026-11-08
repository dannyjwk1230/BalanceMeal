package com.example.balancemeal.model

import java.util.UUID
import kotlinx.serialization.Serializable

/** 입력 도중의 빈 값이나 소수점 등을 손실 없이 유지합니다. ID는 행 생성 시 한 번 만듭니다. */
@Serializable
data class MealItemDraft(
    val itemId: String = UUID.randomUUID().toString(),
    val food: Food? = null,
    val amountInput: String = ""
)
