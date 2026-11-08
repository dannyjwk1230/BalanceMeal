package com.example.balancemeal.data

import com.example.balancemeal.model.MealRecord
import kotlinx.coroutines.flow.Flow

interface MealRepository {
    /** 최초 읽기가 완료될 때까지 대기합니다. 실패는 예외로 전달하며 재시도할 수 있습니다. */
    suspend fun initialize()

    /** 초기화 성공 전에는 방출하지 않습니다. 빈 목록은 성공적으로 읽은 빈 상태입니다. */
    fun observeRecords(): Flow<List<MealRecord>>

    /** 초기화 성공 후에만 저장합니다. 날짜·끼니별 생성/갱신 시 기존 ID를 유지합니다. 기존 중복은 오류로 전달하고 보존합니다. */
    suspend fun saveMeal(record: MealRecord)

    /** 초기화 성공 후에만 삭제합니다. 해당 recordId가 없으면 조용히 성공합니다. */
    suspend fun deleteMeal(recordId: String)
}
