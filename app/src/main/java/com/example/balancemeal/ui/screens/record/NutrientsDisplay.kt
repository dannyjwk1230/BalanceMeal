package com.example.balancemeal.ui.screens.record

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.balancemeal.model.Nutrients
import java.util.Locale

/** 계산은 하지 않고 전달받은 원본 Double을 표시할 때만 소수 첫째 자리로 포맷합니다. */
@Composable
fun NutrientsDisplay(nutrients: Nutrients) {
    fun format(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
    Column {
        Text("탄수화물 ${format(nutrients.carbohydrateG)} g")
        Text("단백질 ${format(nutrients.proteinG)} g")
        Text("지방 ${format(nutrients.fatG)} g")
        Text("열량 ${format(nutrients.energyKcal)} kcal")
    }
}
