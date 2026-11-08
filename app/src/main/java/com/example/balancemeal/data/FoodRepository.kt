package com.example.balancemeal.data

import com.example.balancemeal.model.Food
import com.example.balancemeal.model.SourceInfo

interface FoodRepository {
    /** 읽기, 파싱, 검증 실패는 예외로 전달합니다. */
    suspend fun getFoods(): List<Food>
    suspend fun getSourceInfo(): SourceInfo
}
