package com.example.balancemeal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.balancemeal.model.MealType
import com.example.balancemeal.ui.meal.MealEvent
import com.example.balancemeal.ui.meal.MealViewModel
import com.example.balancemeal.ui.meal.OperationState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedMealViewModelInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun foodRowsAddEditShowUnitsAndDeleteIndependently() {
        lateinit var shared: MealViewModel
        compose.activityRule.scenario.onActivity { activity ->
            val container = (activity.application as BalanceMealApplication).appContainer
            shared = ViewModelProvider(activity, container.mealViewModelFactory)[MealViewModel::class.java]
        }
        compose.waitUntil(10_000) { shared.uiState.value.foodReadState == OperationState.Success }
        compose.onNodeWithText("기록으로 이동").performClick()
        compose.onNodeWithText("음식·반찬 추가").performScrollTo().performClick()
        compose.onNodeWithText("음식·반찬 추가").performScrollTo().performClick()
        val rows = shared.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items
        assertEquals(2, rows.size)
        val first = rows[0].itemId
        val second = rows[1].itemId
        assertNotEquals(first, second)
        compose.onAllNodesWithText("음식을 선택해 주세요.").assertCountEquals(2)
        compose.onNodeWithTag("amount-$first").performScrollTo().performTextInput("0.")
        compose.onNodeWithTag("amount-$second").performScrollTo().performTextInput("125.5")
        val gramFood = shared.uiState.value.foods.first { it.unit == com.example.balancemeal.model.AmountUnit.G }
        val mlFood = shared.uiState.value.foods.first { it.unit == com.example.balancemeal.model.AmountUnit.ML }
        compose.runOnIdle {
            shared.onEvent(MealEvent.SelectFood(first, gramFood.code, MealType.BREAKFAST))
            shared.onEvent(MealEvent.SelectFood(second, mlFood.code, MealType.BREAKFAST))
        }
        compose.onNodeWithText("섭취량(g)").assertExists()
        compose.onNodeWithText("섭취량(ml)").assertExists()
        compose.runOnIdle {
            shared.onEvent(MealEvent.SelectFood(second, gramFood.code, MealType.BREAKFAST))
            assertEquals("", shared.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items[1].amountInput)
            shared.onEvent(MealEvent.SetAmount(second, "125.5"))
        }
        compose.onNodeWithTag("delete-item-$first").performScrollTo().performClick()
        compose.onNodeWithTag("meal-item-$first").assertDoesNotExist()
        compose.onNodeWithTag("amount-$second").performScrollTo().assertTextContains("125.5")
        val remaining = shared.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items
        assertEquals(second, remaining.single().itemId)
        assertEquals("125.5", remaining.single().amountInput)
        compose.onNodeWithTag("delete-item-$second").performScrollTo().performClick()
        assertTrue(shared.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.isEmpty())
    }

    @Test
    fun foodSearchSelectionReplacementCancellationAndDeletedTarget() {
        lateinit var shared: MealViewModel
        compose.activityRule.scenario.onActivity { activity ->
            val container = (activity.application as BalanceMealApplication).appContainer
            shared = ViewModelProvider(activity, container.mealViewModelFactory)[MealViewModel::class.java]
        }
        compose.waitUntil(10_000) { shared.uiState.value.foodReadState == OperationState.Success }
        compose.onNodeWithText("기록으로 이동").performClick()
        compose.onNodeWithText("음식·반찬 추가").performScrollTo().performClick()
        val id = shared.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().itemId
        compose.onNodeWithTag("select-food-$id").performScrollTo().performClick()
        compose.onNodeWithTag("food-search").performTextReplacement("존재하지않는음식")
        compose.onNodeWithText("검색 결과가 없습니다.").assertIsDisplayed()
        compose.onNodeWithTag("food-search").performTextReplacement("쌀밥")
        compose.onNodeWithText("100g 기준").assertIsDisplayed()
        val rice = shared.uiState.value.foods.single { it.name == "쌀밥" }
        compose.onNodeWithTag("food-${rice.code}").performClick()
        compose.onNodeWithTag("amount-$id").performScrollTo().performTextInput("120")
        compose.onNodeWithTag("select-food-$id").performScrollTo().performClick()
        compose.onNodeWithTag("food-search").performTextReplacement("미숫가루")
        compose.onNodeWithText("100ml 기준").assertIsDisplayed()
        val drink = shared.uiState.value.foods.single { it.name == "미숫가루" }
        compose.onNodeWithTag("food-${drink.code}").performClick()
        assertEquals("", shared.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().amountInput)
        compose.onNodeWithTag("amount-$id").performScrollTo().performTextInput("80")
        compose.onNodeWithTag("select-food-$id").performScrollTo().performClick()
        compose.onNodeWithTag("food-search").performTextReplacement("쌀밥")
        compose.onNodeWithText("돌아가기").performClick()
        val unchanged = shared.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single()
        assertEquals(drink.code, unchanged.food?.code)
        assertEquals("80", unchanged.amountInput)
        compose.onNodeWithTag("select-food-$id").performScrollTo().performClick()
        compose.runOnIdle {
            shared.onEvent(MealEvent.RemoveItem(id))
            shared.onEvent(MealEvent.AddItem)
        }
        compose.onNodeWithTag("food-search").performTextReplacement("쌀밥")
        compose.onNodeWithTag("food-${rice.code}").assertIsNotEnabled()
        val replacement = shared.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single()
        assertNotEquals(id, replacement.itemId)
        assertNull(replacement.food)
    }

    @Test
    fun nutritionPreviewUpdatesLiveAndIncompleteRowsHideMealTotal() {
        lateinit var shared: MealViewModel
        compose.activityRule.scenario.onActivity { activity ->
            val container = (activity.application as BalanceMealApplication).appContainer
            shared = ViewModelProvider(activity, container.mealViewModelFactory)[MealViewModel::class.java]
        }
        compose.waitUntil(10_000) { shared.uiState.value.foodReadState == OperationState.Success }
        compose.runOnIdle { shared.onEvent(MealEvent.AddItem) }
        val id = shared.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().itemId
        val rice = shared.uiState.value.foods.single { it.name == "쌀밥" }
        compose.runOnIdle { shared.onEvent(MealEvent.SelectFood(id, rice.code, MealType.BREAKFAST)) }
        compose.onNodeWithText("기록으로 이동").performClick()
        compose.onNodeWithTag("amount-$id").performScrollTo().performTextInput("125")
        val energy = String.format(java.util.Locale.getDefault(), "열량 %.1f kcal", 207.5)
        compose.onAllNodesWithText(energy).assertCountEquals(2)
        compose.onNodeWithText("식사 합계").assertExists()
        assertEquals(46.6625, shared.uiState.value.nutritionPreview.total!!.carbohydrateG, 0.000000001)
        compose.onNodeWithText("음식·반찬 추가").performScrollTo().performClick()
        compose.onNodeWithText("식사 합계").assertDoesNotExist()
        compose.onAllNodesWithText(energy).assertCountEquals(1)
        assertNull(shared.uiState.value.nutritionPreview.total)
    }

    @Test
    fun mealDraftSurvivesRotationAndWideNarrowWindowChanges() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        lateinit var shared: MealViewModel
        compose.activityRule.scenario.onActivity { activity ->
            val container = (activity.application as BalanceMealApplication).appContainer
            shared = ViewModelProvider(activity, container.mealViewModelFactory)[MealViewModel::class.java]
        }
        compose.waitUntil(10_000) { shared.uiState.value.foodReadState == OperationState.Success }
        compose.onNodeWithText("기록으로 이동").performClick()
        compose.onNodeWithText("음식·반찬 추가").performScrollTo().performClick()
        val id = shared.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().itemId
        compose.onNodeWithTag("amount-$id").performScrollTo().performTextInput("123.45")
        compose.onNodeWithText("중식").performScrollTo().performClick()
        compose.onNodeWithText("음식·반찬 추가").performScrollTo().performClick()
        val lunchId = shared.uiState.value.draftsByMealType.getValue(MealType.LUNCH).items.single().itemId
        compose.onNodeWithTag("amount-$lunchId").performScrollTo().performTextInput("222")
        compose.onNodeWithText("조식").performScrollTo().performClick()
        compose.onNodeWithTag("amount-$id").performScrollTo().assertTextContains("123.45")
        val before = shared.uiState.value.draftsByMealType
        assertEquals("222", before.getValue(MealType.LUNCH).items.single().amountInput)
        val originalOrientation = compose.activity.requestedOrientation
        fun shell(command: String): String =
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)
            ).bufferedReader().use { it.readText() }
        val originalSize = shell("wm size")
        val overrideSize = Regex("Override size: (\\d+x\\d+)").find(originalSize)?.groupValues?.get(1)
        try {
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            compose.activityRule.scenario.onActivity {
                assertSame(shared, ViewModelProvider(it,
                    (it.application as BalanceMealApplication).appContainer.mealViewModelFactory)[MealViewModel::class.java])
                it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_PORTRAIT
            }
            shell("wm size 2400x1800")
            compose.waitUntil(10_000) { compose.activity.resources.configuration.screenWidthDp >= 600 }
            compose.waitForIdle()
            assertEquals(before, shared.uiState.value.draftsByMealType)
            val selectedItem = hasText("기록") and SemanticsMatcher.keyIsDefined(
                androidx.compose.ui.semantics.SemanticsProperties.Selected)
            val wideBounds = compose.onNode(selectedItem).getUnclippedBoundsInRoot()
            assertTrue("Wide navigation should be at the side", wideBounds.left.value < 120f)
            shell(if (overrideSize == null) "wm size reset" else "wm size $overrideSize")
            compose.waitUntil(10_000) { compose.activity.resources.configuration.screenWidthDp < 600 }
            compose.waitForIdle()
            assertEquals(before, shared.uiState.value.draftsByMealType)
            compose.onNodeWithTag("amount-$id").performScrollTo().assertTextContains("123.45")
        } finally {
            shell(if (overrideSize == null) "wm size reset" else "wm size $overrideSize")
            compose.activityRule.scenario.onActivity { it.requestedOrientation = originalOrientation }
        }
    }

    @Test
    fun dateTimeDialogsUpdateSelectedMealAndKeepHomeDateSeparate() {
        lateinit var shared: MealViewModel
        compose.activityRule.scenario.onActivity { activity ->
            val container = (activity.application as BalanceMealApplication).appContainer
            shared = ViewModelProvider(activity, container.mealViewModelFactory)[MealViewModel::class.java]
            shared.onEvent(MealEvent.SetDate(java.time.LocalDate.of(2026, 11, 8)))
            shared.onEvent(MealEvent.SetTime(java.time.LocalTime.of(12, 30)))
        }
        val homeDate = shared.uiState.value.homeDate
        compose.onNodeWithText("기록으로 이동").performClick()
        compose.onNodeWithText("식사 날짜: 2026-11-08").performScrollTo().performClick()
        compose.onNodeWithText("날짜 적용").assertIsDisplayed()
        compose.onNodeWithText("November 15, 2026", substring = true).performClick()
        compose.onNodeWithText("날짜 적용").performClick()
        compose.onNodeWithText("식사 날짜: 2026-11-15").assertIsDisplayed()
        compose.onNodeWithText("식사 시간: 12:30").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("07")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("45")
        compose.onNodeWithText("시간 적용").performClick()
        compose.onNodeWithText("식사 시간: 07:45").assertIsDisplayed()
        compose.onNodeWithText("중식").performClick()
        compose.onNodeWithText("조식").performClick()
        compose.onNodeWithText("식사 날짜: 2026-11-15").assertIsDisplayed()
        compose.onNodeWithText("식사 시간: 07:45").assertIsDisplayed()
        assertEquals(homeDate, shared.uiState.value.homeDate)
        compose.onNodeWithText("식사 날짜: 2026-11-15").performClick()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("식사 날짜: 2026-11-15").assertIsDisplayed()
    }

    @Test
    fun tabsFoodSelectionAndActivityRecreationShareOneViewModel() {
        lateinit var shared: MealViewModel
        compose.activityRule.scenario.onActivity { activity ->
            val container = (activity.application as BalanceMealApplication).appContainer
            shared = ViewModelProvider(activity, container.mealViewModelFactory)[MealViewModel::class.java]
        }
        compose.waitUntil(10_000) {
            shared.uiState.value.foodReadState == OperationState.Success &&
                shared.uiState.value.recordReadState == OperationState.Success
        }
        assertEquals(100, shared.uiState.value.foods.size)
        compose.runOnIdle { shared.onEvent(MealEvent.AddItem) }
        val itemId = shared.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().itemId

        compose.onNodeWithText("기록으로 이동").performClick()
        compose.onNodeWithText("음식 선택").performScrollTo().performClick()
        compose.onNodeWithText("음식 검색").assertIsDisplayed()
        compose.runOnIdle {
            shared.onEvent(MealEvent.SelectFood(itemId, shared.uiState.value.foods.first().code, MealType.BREAKFAST))
            shared.onEvent(MealEvent.SetAmount(itemId, "120.5"))
        }
        compose.onNodeWithText("돌아가기").performClick()
        compose.onNodeWithText("홈").performClick()
        compose.onNodeWithText("기록으로 이동").performClick()
        assertEquals("120.5",
            shared.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().amountInput)

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { activity ->
            val container = (activity.application as BalanceMealApplication).appContainer
            val retained = ViewModelProvider(activity, container.mealViewModelFactory)[MealViewModel::class.java]
            assertSame(shared, retained)
            assertEquals(itemId, retained.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().itemId)
            assertNotNull(retained.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().food)
        }
    }
}
