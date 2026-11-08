package com.example.balancemeal.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.balancemeal.nutrition.calculateNutrients
import com.example.balancemeal.ui.meal.MealEvent
import com.example.balancemeal.ui.meal.MealUiState
import kotlin.math.roundToInt

@Composable
fun MealDetailScreen(recordId: String, state: MealUiState, onEvent: (MealEvent) -> Unit) {
    val record = state.records.find { it.recordId == recordId }
    if (record == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("식사 기록을 찾을 수 없습니다.")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 상단: 식사 사진
        if (record.photoReference != null) {
            AsyncImage(
                model = record.photoReference,
                contentDescription = "식사 사진",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "사진 없음", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // 상단: 날짜 및 시간 정보, 식사 종류
            Text(
                text = "${record.date} ${record.time}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = record.mealType.displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "음식 목록",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // 음식 목록 및 영양소 계산
            var totalCarb = 0.0
            var totalProtein = 0.0
            var totalFat = 0.0
            var totalKcal = 0.0
            
            record.items.forEach { item ->
                // 실제 먹은 양을 기준으로 영양소 계산
                val actualNutrients = calculateNutrients(item.food.nutrientsPer100, item.amount)
                
                if (actualNutrients != null) {
                    totalCarb += actualNutrients.carbohydrateG
                    totalProtein += actualNutrients.proteinG
                    totalFat += actualNutrients.fatG
                    totalKcal += actualNutrients.energyKcal
                }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${item.food.name} (${item.amount} ${item.food.unit.name.lowercase()})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (actualNutrients != null) {
                            Text(
                                text = "열량: ${actualNutrients.energyKcal.roundToInt()} kcal | " +
                                       "탄수화물: ${actualNutrients.carbohydrateG.roundToInt()}g | " +
                                       "단백질: ${actualNutrients.proteinG.roundToInt()}g | " +
                                       "지방: ${actualNutrients.fatG.roundToInt()}g",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                text = "영양 정보 계산 불가",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 하단: 총 합산 열량 및 영양소 강조
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "총 합산 영양소",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Total: ${totalKcal.roundToInt()} kcal",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "탄수화물: ${totalCarb.roundToInt()}g\n" +
                               "단백질: ${totalProtein.roundToInt()}g\n" +
                               "지방: ${totalFat.roundToInt()}g",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
