package com.example.balancemeal.data

interface PhotoStorage {
    /** 원본 참조를 내부 이미지 폴더로 스트림 복사하고 새 파일 참조를 반환합니다. 실패는 예외로 전달합니다. */
    suspend fun copyPhoto(sourceReference: String): String

    /** 이번 저장 시도에서 생성했고 기록에 연결되지 않은 내부 파일만 정리합니다. */
    suspend fun deletePhoto(reference: String)
}
