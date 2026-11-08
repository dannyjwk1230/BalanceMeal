package com.example.balancemeal.ui.screens.record

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.balancemeal.model.Nutrients
import com.example.balancemeal.ui.theme.BalanceMealTheme

// 프리뷰 전용 수치이며 운영 기록이나 Repository에는 등록하지 않습니다.
@Preview(name = "Phone portrait", widthDp = 320, heightDp = 900, showBackground = true)
@Preview(name = "Phone landscape", widthDp = 800, heightDp = 400, showBackground = true)
@Preview(name = "Tablet", widthDp = 1200, heightDp = 800, showBackground = true)
@Composable
fun NutritionDisplayPreview() {
    BalanceMealTheme {
        Surface {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MealNutritionSummary(Nutrients(74.6, 17.0, 4.2, 415.0))
                OutlinedCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("음식별 영양 정보")
                        NutrientsDisplay(Nutrients(37.3, 8.5, 2.1, 207.5))
                    }
                }
                OutlinedCard(Modifier.widthIn(max = 340.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("좁은 영역의 음식별 영양 정보")
                        NutrientsDisplay(Nutrients(10.0, 5.0, 2.0, 100.0))
                    }
                }
                MealNutritionSummary(null)
            }
        }
    }
}
