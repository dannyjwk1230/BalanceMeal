package com.example.balancemeal.ui.screens.record

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.balancemeal.model.AmountUnit
import com.example.balancemeal.model.MealItemDraft

@Composable
fun MealItemRow(
    item: MealItemDraft,
    enabled: Boolean = true,
    error: String?,
    nutrients: com.example.balancemeal.model.Nutrients?,
    onSelectFood: () -> Unit,
    onAmountChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("meal-item-${item.itemId}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSelectFood,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).testTag("select-food-${item.itemId}")
                ) {
                    Text(item.food?.let { "음식 변경: ${it.name}" } ?: "음식 선택")
                }
                TextButton(
                    onClick = onDelete,
                    enabled = enabled,
                    modifier = Modifier.testTag("delete-item-${item.itemId}")
                ) { Text("삭제") }
            }
            if (item.food == null) Text("음식을 선택해 주세요.")
            val label = when (item.food?.unit) {
                AmountUnit.G -> "섭취량(g)"
                AmountUnit.ML -> "섭취량(ml)"
                null -> "섭취량"
            }
            OutlinedTextField(
                value = item.amountInput,
                enabled = enabled,
                onValueChange = onAmountChange,
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = error != null,
                modifier = Modifier.fillMaxWidth().testTag("amount-${item.itemId}")
            )
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            if (nutrients != null) NutrientsDisplay(nutrients)
            else Text("음식과 유효한 섭취량을 입력하면 영양소가 표시됩니다.")
        }
    }
}
