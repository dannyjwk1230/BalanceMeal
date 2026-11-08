package com.example.balancemeal.ui.screens.history

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.balancemeal.model.AmountUnit
import com.example.balancemeal.model.Food
import com.example.balancemeal.model.MealItem
import com.example.balancemeal.model.MealRecord
import com.example.balancemeal.model.MealType
import com.example.balancemeal.model.Nutrients
import com.example.balancemeal.ui.meal.MealUiState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class HistoryScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun historyScreenDisplaysRecordsAndHandlesClicks() {
        val date = LocalDate.of(2026, 11, 8)
        val time = LocalTime.of(12, 30)

        val food = Food("001", "김치찌개", AmountUnit.G, Nutrients(10.0, 5.0, 2.0, 100.0))
        val item = MealItem(food = food, amount = 200.0)
        val record = MealRecord(
            recordId = "test-record-id",
            date = date,
            time = time,
            mealType = MealType.LUNCH,
            photoReference = null,
            items = listOf(item)
        )

        val state = MealUiState(records = listOf(record))
        var selectedRecordId: String? = null

        composeTestRule.setContent {
            HistoryScreen(
                state = state,
                onEvent = {},
                onMealSelected = { selectedRecordId = it }
            )
        }

        // 요약 카드가 렌더링 되었는지 확인 (총 열량: 200 kcal, 끼니 수: 1)
        composeTestRule.onNodeWithText("요약").assertIsDisplayed()
        composeTestRule.onNodeWithText("끼니 수: 1끼").assertIsDisplayed()
        composeTestRule.onNodeWithText("총 열량: 200 kcal").assertIsDisplayed()

        // 아이템 카드가 표시되는지 확인
        composeTestRule.onNodeWithText("2026-11-08 12:30").assertIsDisplayed()
        composeTestRule.onNodeWithText(MealType.LUNCH.displayName).assertIsDisplayed()

        // 클릭 시 이벤트 처리가 잘 일어나는지 검증
        composeTestRule.onNodeWithText(MealType.LUNCH.displayName).performClick()

        assertEquals("test-record-id", selectedRecordId)
    }
}
