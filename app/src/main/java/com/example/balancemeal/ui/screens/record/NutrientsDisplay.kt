package com.example.balancemeal.ui.screens.record

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.balancemeal.model.Nutrients
import java.util.Locale

/** 원본 Double을 표시할 때만 소수 첫째 자리로 포맷합니다. 계산은 ViewModel이 담당합니다. */
@Composable
fun NutrientsDisplay(nutrients: Nutrients, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val entries = listOf(
        NutrientEntry("탄수화물", nutrients.carbohydrateG, "g", colors.primaryContainer, colors.onPrimaryContainer),
        NutrientEntry("지방", nutrients.fatG, "g", colors.tertiaryContainer, colors.onTertiaryContainer),
        NutrientEntry("단백질", nutrients.proteinG, "g", colors.secondaryContainer, colors.onSecondaryContainer),
        NutrientEntry("열량", nutrients.energyKcal, "kcal", colors.surfaceVariant, colors.onSurfaceVariant)
    )
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    // 기기 전체가 아닌 실제 카드 내부 너비로 결정합니다. 큰 글꼴에는 셀 공간을 더 줍니다.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= (4 * 144 * fontScale + 3 * 8).dp -> 4
            maxWidth >= (2 * 88 * fontScale + 8).dp -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { entry ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            color = entry.container,
                            contentColor = entry.foreground
                        ) {
                            Column(Modifier.padding(12.dp).heightIn(min = if (emphasized) 100.dp else 80.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(entry.name, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(String.format(Locale.getDefault(), "%.1f", entry.value))
                                        }
                                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.labelLarge.fontSize,
                                            fontWeight = FontWeight.Medium)) { append(" ${entry.unit}") }
                                    },
                                    style = if (emphasized) MaterialTheme.typography.headlineMedium
                                        else MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class NutrientEntry(
    val name: String, val value: Double, val unit: String,
    val container: Color, val foreground: Color
)

@Composable
fun MealNutritionSummary(total: Nutrients?, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier.fillMaxWidth().border(
            2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large
        ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.primary))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("영양소 미리보기", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            if (total != null) {
                Text("식사 전체 영양 합계", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                NutrientsDisplay(total, emphasized = true)
            } else {
                Text("모든 음식과 섭취량이 유효해야 식사 합계를 표시합니다.")
            }
        }
    }
}
