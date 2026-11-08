package com.example.balancemeal

import android.app.Application

class BalanceMealApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // 공유 의존성을 준비합니다. 실제 초기 읽기는 공유 MealViewModel이 시작합니다.
        appContainer
    }
}
