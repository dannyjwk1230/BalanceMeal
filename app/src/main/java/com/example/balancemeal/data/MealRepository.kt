package com.example.balancemeal.data

import com.example.balancemeal.model.MealRecord
import kotlinx.coroutines.flow.Flow

interface MealRepository {
    /** 최초 읽기가 완료될 때까지 대기합니다. 실패는 예외로 전달하며 재시도할 수 있습니다. */
    suspend fun initialize()

    /** 초기화 성공 전에는 방출하지 않습니다. 빈 목록은 성공적으로 읽은 빈 상태입니다. */
    fun observeRecords(): Flow<List<MealRecord>>

    /** 초기화 성공 후에만 저장합니다. 같은 recordId는 갱신하고 새 ID는 추가합니다. */
    suspend fun saveMeal(record: MealRecord)
}
