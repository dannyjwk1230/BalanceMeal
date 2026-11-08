package com.example.balancemeal.ui.meal

import androidx.lifecycle.ViewModelStore
import com.example.balancemeal.data.FoodRepository
import com.example.balancemeal.data.MealRepository
import com.example.balancemeal.model.*
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MealViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2026-11-08T03:30:00Z"), ZoneId.of("Asia/Seoul"))
    private val food = Food("001", "테스트", AmountUnit.G, Nutrients(1.0, 2.0, 3.0, 39.0))
    private val foods = FakeFoods()
    private val meals = FakeMeals()
    private var photoCopies = 0
    private var failPhotoCopy = false
    private var failPhotoDelete = false
    private val deletedPhotos = mutableListOf<String>()
    private val photoStorage = object : com.example.balancemeal.data.PhotoStorage {
        override suspend fun copyPhoto(sourceReference: String): String {
            photoCopies++
            if (failPhotoCopy) throw IOException("photo copy failed")
            return "file:///test/photo-$photoCopies"
        }
        override suspend fun deletePhoto(reference: String) {
            deletedPhotos.add(reference)
            if (failPhotoDelete) throw IOException("photo cleanup failed")
        }
    }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private fun viewModel() = MealViewModel(foods, meals, clock, photoStorage).also { store.put("meal", it) }
    private fun fillDraft(vm: MealViewModel): String {
        vm.onEvent(MealEvent.SetPhoto("content://test/selected", vm.uiState.value.currentMeal))
        vm.onEvent(MealEvent.AddItem)
        val id = vm.uiState.value.draftsByMealType.getValue(vm.uiState.value.currentMeal).items.single().itemId
        vm.onEvent(MealEvent.SelectFood(id, food.code, MealType.BREAKFAST))
        vm.onEvent(MealEvent.SetAmount(id, "125.5"))
        return id
    }

    @Test fun initialLoadsUseClockAndRemainIndependent() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(OperationState.Loading, vm.uiState.value.foodReadState)
        assertEquals(OperationState.Loading, vm.uiState.value.recordReadState)
        assertEquals(OperationState.Idle, vm.uiState.value.saveState)
        runCurrent()
        val state = vm.uiState.value
        assertEquals(OperationState.Success, state.foodReadState)
        assertEquals(OperationState.Success, state.recordReadState)
        assertEquals(listOf(food), state.foods)
        assertNotNull(state.sourceInfo)
        assertEquals(4, state.draftsByMealType.size)
        assertTrue(state.draftsByMealType.values.all {
            it.date == LocalDate.of(2026, 11, 8) && it.time == LocalTime.of(12, 30)
        })
    }

    @Test fun onlyFailedFoodReadingRetriesAndDuplicateRetriesAreIgnored() = runTest(dispatcher) {
        foods.failFoods = true
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.uiState.value.foodReadState is OperationState.Error)
        assertEquals(OperationState.Success, vm.uiState.value.recordReadState)
        foods.failFoods = false
        vm.onEvent(MealEvent.RetryRecordReading)
        vm.onEvent(MealEvent.RetryFoodReading)
        vm.onEvent(MealEvent.RetryFoodReading)
        runCurrent()
        vm.onEvent(MealEvent.RetryFoodReading)
        runCurrent()
        assertEquals(2, foods.foodCalls)
        assertEquals(1, meals.initializeCalls)
        assertEquals(1, meals.observationCalls)
        assertEquals(OperationState.Success, vm.uiState.value.foodReadState)
    }

    @Test fun sourceFailureRetriesSourceWithoutReloadingFoods() = runTest(dispatcher) {
        foods.failSource = true
        val vm = viewModel()
        runCurrent()
        assertEquals(listOf(food), vm.uiState.value.foods)
        assertTrue(vm.uiState.value.foodReadState is OperationState.Error)
        foods.failSource = false
        vm.onEvent(MealEvent.RetryFoodReading)
        runCurrent()
        assertEquals(1, foods.foodCalls)
        assertEquals(2, foods.sourceCalls)
        assertEquals(OperationState.Success, vm.uiState.value.foodReadState)
    }

    @Test fun recordFailureBlocksSavingAndRetriesOnlyRecordReading() = runTest(dispatcher) {
        meals.failInitialize = true
        val vm = viewModel()
        runCurrent()
        fillDraft(vm)
        vm.onEvent(MealEvent.SaveCurrentMeal)
        assertEquals(0, meals.saveCalls)
        assertTrue("records" in vm.uiState.value.inputErrors)
        meals.failInitialize = false
        vm.onEvent(MealEvent.RetryRecordReading)
        runCurrent()
        assertEquals(2, meals.initializeCalls)
        assertEquals(1, foods.foodCalls)
        assertEquals(OperationState.Success, vm.uiState.value.recordReadState)
        val record = MealRecord("external", LocalDate.now(clock), LocalTime.NOON,
            MealType.LUNCH, items = emptyList())
        meals.records.value = listOf(record)
        runCurrent()
        assertEquals(listOf(record), vm.uiState.value.records)
    }

    @Test fun mealDraftsAreIndependentAndInvalidInputNeverSaves() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        val id = fillDraft(vm)
        vm.onEvent(MealEvent.SetAmount(id, "0."))
        vm.onEvent(MealEvent.SelectMeal(MealType.LUNCH))
        assertTrue(vm.uiState.value.draftsByMealType.getValue(MealType.LUNCH).items.isEmpty())
        vm.onEvent(MealEvent.SelectMeal(MealType.BREAKFAST))
        assertEquals("0.", vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().amountInput)
        vm.onEvent(MealEvent.SaveCurrentMeal)
        assertTrue(id in vm.uiState.value.inputErrors)
        assertEquals(0, meals.saveCalls)
        vm.onEvent(MealEvent.SetAmount(id, "NaN"))
        vm.onEvent(MealEvent.SaveCurrentMeal)
        assertEquals(0, meals.saveCalls)
    }

    @Test fun failedSavePreservesDraftAndRetryKeepsRecordId() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        fillDraft(vm)
        val draft = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST)
        meals.failSave = true
        vm.onEvent(MealEvent.SaveCurrentMeal)
        vm.onEvent(MealEvent.SaveCurrentMeal)
        runCurrent()
        assertEquals(1, meals.saveCalls)
        assertTrue(vm.uiState.value.saveState is OperationState.Error)
        assertEquals(draft, vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST))
        assertTrue(vm.uiState.value.records.isEmpty())
        meals.failSave = false
        vm.onEvent(MealEvent.RetrySave)
        vm.onEvent(MealEvent.RetrySave)
        runCurrent()
        assertEquals(2, meals.saveCalls)
        assertEquals(meals.attempts[0].recordId, meals.attempts[1].recordId)
        assertEquals(OperationState.Success, vm.uiState.value.saveState)
        assertEquals(1, vm.uiState.value.records.size)
        assertTrue(vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.isEmpty())
        assertEquals(1, foods.foodCalls)
        assertEquals(1, meals.initializeCalls)
    }

    @Test fun savingDraftIsLockedWhileOtherMealRemainsEditable() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        val id = fillDraft(vm)
        val original = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST)
        val gate = CompletableDeferred<Unit>()
        meals.saveGate = gate
        vm.saveMeal()
        vm.saveMeal()
        assertEquals(MealType.BREAKFAST, vm.uiState.value.savingMealType)
        runCurrent()
        vm.onEvent(MealEvent.SetAmount(id, "200"))
        vm.onEvent(MealEvent.RemoveItem(id))
        vm.onEvent(MealEvent.AddItem)
        vm.onEvent(MealEvent.SetDate(LocalDate.of(2020, 1, 1)))
        vm.onEvent(MealEvent.SetPhoto("changed", MealType.BREAKFAST))
        assertEquals(original, vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST))
        vm.onEvent(MealEvent.SelectMeal(MealType.LUNCH))
        vm.onEvent(MealEvent.AddItem)
        vm.onEvent(MealEvent.SetTime(LocalTime.of(14, 10)))
        val lunch = vm.uiState.value.draftsByMealType.getValue(MealType.LUNCH)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, meals.saveCalls)
        val cleared = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST)
        assertEquals(original.date, cleared.date)
        assertEquals(original.time, cleared.time)
        assertNull(cleared.photoReference)
        assertTrue(cleared.items.isEmpty())
        assertEquals(lunch, vm.uiState.value.draftsByMealType.getValue(MealType.LUNCH))
        assertNull(vm.uiState.value.savingMealType)
        assertEquals("file:///test/photo-1", vm.uiState.value.records.single().photoReference)
        assertTrue(deletedPhotos.isEmpty())
    }

    @Test fun datesAndTimesStayPerMealAndDoNotChangeHomeDate() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        val home = vm.uiState.value.homeDate
        vm.onEvent(MealEvent.SetDate(LocalDate.of(2026, 10, 20)))
        vm.onEvent(MealEvent.SetTime(LocalTime.of(7, 45)))
        vm.onEvent(MealEvent.SelectMeal(MealType.LUNCH))
        assertEquals(LocalDate.of(2026, 11, 8), vm.uiState.value.draftsByMealType.getValue(MealType.LUNCH).date)
        vm.onEvent(MealEvent.SetDate(LocalDate.of(2026, 11, 1)))
        vm.onEvent(MealEvent.SetTime(LocalTime.of(13, 10)))
        vm.onEvent(MealEvent.SelectMeal(MealType.BREAKFAST))
        assertEquals(LocalDate.of(2026, 10, 20), vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).date)
        assertEquals(LocalTime.of(7, 45), vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).time)
        assertEquals(home, vm.uiState.value.homeDate)
        vm.onEvent(MealEvent.SetHomeDate(LocalDate.of(2026, 9, 1)))
        assertEquals(LocalDate.of(2026, 10, 20), vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).date)
        assertEquals(0, meals.saveCalls)
    }

    @Test fun injectedClockZoneDeterminesDateAcrossUtcMidnight() = runTest(dispatcher) {
        val zoneClock = Clock.fixed(Instant.parse("2026-11-07T23:30:00Z"), ZoneId.of("Asia/Seoul"))
        val vm = MealViewModel(foods, meals, zoneClock, photoStorage).also { store.put("meal", it) }
        runCurrent()
        assertTrue(vm.uiState.value.draftsByMealType.values.all {
            it.date == LocalDate.of(2026, 11, 8) && it.time == LocalTime.of(8, 30)
        })
        assertEquals(LocalDate.of(2026, 11, 8), vm.uiState.value.homeDate)
    }

    @Test fun duplicateFoodsHaveIndependentIdsAmountsAndDeletion() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.onEvent(MealEvent.AddItem)
        vm.onEvent(MealEvent.AddItem)
        val initial = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items
        val first = initial[0].itemId
        val second = initial[1].itemId
        assertNotEquals(first, second)
        vm.onEvent(MealEvent.SelectFood(first, food.code, MealType.BREAKFAST))
        vm.onEvent(MealEvent.SelectFood(second, food.code, MealType.BREAKFAST))
        vm.onEvent(MealEvent.SetAmount(first, "0."))
        vm.onEvent(MealEvent.SetAmount(second, "001.50"))
        val rows = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items
        assertEquals(rows[0].food, rows[1].food)
        assertEquals("0.", rows[0].amountInput)
        assertEquals("001.50", rows[1].amountInput)
        vm.onEvent(MealEvent.RemoveItem(first))
        assertEquals(listOf(rows[1]), vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items)
        assertEquals(0, meals.saveCalls)
    }

    @Test fun selectionUsesOriginalMealAndMissingTargetNeverChangesAnotherRow() = runTest(dispatcher) {
        val other = food.copy(code = "002", name = "다른 음식", unit = AmountUnit.ML)
        foods.availableFoods = listOf(food, other)
        val vm = viewModel()
        runCurrent()
        val first = fillDraft(vm)
        vm.onEvent(MealEvent.SelectMeal(MealType.LUNCH))
        vm.onEvent(MealEvent.AddItem)
        val lunch = vm.uiState.value.draftsByMealType.getValue(MealType.LUNCH).items.single().itemId
        vm.onEvent(MealEvent.SetAmount(lunch, "50"))
        vm.onEvent(MealEvent.SelectFood(first, food.code, MealType.BREAKFAST))
        assertEquals("125.5", vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().amountInput)
        vm.onEvent(MealEvent.SelectFood(first, other.code, MealType.BREAKFAST))
        val breakfast = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single()
        assertEquals(other, breakfast.food)
        assertEquals("", breakfast.amountInput)
        assertEquals("50", vm.uiState.value.draftsByMealType.getValue(MealType.LUNCH).items.single().amountInput)
        vm.onEvent(MealEvent.RemoveItem(lunch))
        vm.onEvent(MealEvent.AddItem)
        val before = vm.uiState.value
        vm.onEvent(MealEvent.SelectFood(lunch, food.code, MealType.LUNCH))
        vm.onEvent(MealEvent.SelectFood(first, "missing-code", MealType.BREAKFAST))
        assertEquals(before, vm.uiState.value)
    }

    @Test fun foodSelectionRouteContainsOnlyTargetIdentifiers() {
        val route = com.example.balancemeal.navigation.FoodSelection(MealType.LUNCH, "target-id")
        val encoded = kotlinx.serialization.json.Json.encodeToString(
            com.example.balancemeal.navigation.FoodSelection.serializer(), route)
        val restored = kotlinx.serialization.json.Json.decodeFromString(
            com.example.balancemeal.navigation.FoodSelection.serializer(), encoded)
        assertEquals(route, restored)
        assertFalse(encoded.contains("nutrientsPer100"))
    }

    @Test fun previewRejectsInvalidStringsAndIncompleteRowsWithoutChangingInput() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        val id = fillDraft(vm)
        for (input in listOf("", " ", "문자", "0", "-1", "NaN", "Infinity", "-Infinity", "1e999")) {
            vm.onEvent(MealEvent.SetAmount(id, input))
            assertEquals(input, vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().amountInput)
            assertTrue(vm.uiState.value.nutritionPreview.byItemId.isEmpty())
            assertNull(vm.uiState.value.nutritionPreview.total)
        }
        vm.onEvent(MealEvent.SetAmount(id, "33.333"))
        val valid = vm.uiState.value.nutritionPreview
        assertEquals(0.33333, valid.total!!.carbohydrateG, 0.000000001)
        vm.onEvent(MealEvent.AddItem)
        assertEquals(valid.byItemId, vm.uiState.value.nutritionPreview.byItemId)
        assertNull(vm.uiState.value.nutritionPreview.total)
        val incomplete = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.last().itemId
        vm.onEvent(MealEvent.RemoveItem(incomplete))
        assertEquals(valid, vm.uiState.value.nutritionPreview)
        vm.onEvent(MealEvent.SelectMeal(MealType.LUNCH))
        assertNull(vm.uiState.value.nutritionPreview.total)
    }

    @Test fun mixedUnitsSumOnlyCalculatedNutrientsWithNoIntermediateRounding() = runTest(dispatcher) {
        val drink = food.copy(code = "002", unit = AmountUnit.ML, nutrientsPer100 = Nutrients(0.1, 0.2, 0.3, 2.0))
        foods.availableFoods = listOf(food, drink)
        val vm = viewModel()
        runCurrent()
        val first = fillDraft(vm)
        vm.onEvent(MealEvent.SetAmount(first, "33.333"))
        vm.onEvent(MealEvent.AddItem)
        val second = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.last().itemId
        vm.onEvent(MealEvent.SelectFood(second, drink.code, MealType.BREAKFAST))
        vm.onEvent(MealEvent.SetAmount(second, "25.5"))
        val preview = vm.uiState.value.nutritionPreview
        assertEquals(2, preview.byItemId.size)
        assertEquals(0.35883, preview.total!!.carbohydrateG, 0.000000001)
        assertEquals(13.50987, preview.total.energyKcal, 0.000000001)
        assertEquals(0, meals.saveCalls)
    }

    @Test fun photoResultTargetsOpeningMealAndCancelPreservesItWithoutCopying() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.onEvent(MealEvent.SetPhoto("content://test/old", MealType.BREAKFAST))
        vm.onEvent(MealEvent.SelectMeal(MealType.LUNCH))
        vm.onEvent(MealEvent.SetPhoto("content://test/new", MealType.BREAKFAST))
        vm.onEvent(MealEvent.SetPhoto(null, MealType.BREAKFAST))
        assertEquals("content://test/new", vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).photoReference)
        assertNull(vm.uiState.value.draftsByMealType.getValue(MealType.LUNCH).photoReference)
        assertEquals(0, photoCopies)
    }

    @Test fun missingInputsAndAnyInvalidRowBlockTheEntireSave() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.saveMeal()
        assertTrue("photo" in vm.uiState.value.inputErrors)
        assertTrue("items" in vm.uiState.value.inputErrors)
        fillDraft(vm)
        vm.onEvent(MealEvent.AddItem)
        val missing = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.last().itemId
        vm.saveMeal()
        assertTrue(missing in vm.uiState.value.inputErrors)
        assertEquals(0, photoCopies)
        assertEquals(0, meals.saveCalls)
        vm.onEvent(MealEvent.SelectFood(missing, food.code, MealType.BREAKFAST))
        for (input in listOf("", "abc", "0", "-1", "NaN", "Infinity", "1e999")) {
            vm.onEvent(MealEvent.SetAmount(missing, input))
            vm.saveMeal()
            assertTrue(missing in vm.uiState.value.inputErrors)
        }
        assertEquals(0, photoCopies)
        assertEquals(0, meals.saveCalls)
    }

    @Test fun failedFoodInitializationAlsoBlocksSaving() = runTest(dispatcher) {
        foods.failFoods = true
        val vm = viewModel()
        runCurrent()
        vm.saveMeal()
        assertTrue("foods" in vm.uiState.value.inputErrors)
        assertEquals(0, photoCopies)
        assertEquals(0, meals.saveCalls)
    }

    @Test fun photoCopyFailureKeepsDraftAndDoesNotCallMealRepositoryOrDeleteSource() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        fillDraft(vm)
        val original = vm.uiState.value.draftsByMealType
        failPhotoCopy = true
        vm.saveMeal()
        runCurrent()
        assertTrue(vm.uiState.value.saveState is OperationState.Error)
        assertEquals(original, vm.uiState.value.draftsByMealType)
        assertEquals(0, meals.saveCalls)
        assertTrue(deletedPhotos.isEmpty())
        assertNull(vm.uiState.value.savingMealType)
    }

    @Test fun recordFailureDeletesOnlyNewCopyAndRetainsCleanupErrors() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        fillDraft(vm)
        val original = vm.uiState.value.draftsByMealType
        meals.failSave = true
        failPhotoDelete = true
        vm.saveMeal()
        runCurrent()
        assertEquals(listOf("file:///test/photo-1"), deletedPhotos)
        assertEquals(original, vm.uiState.value.draftsByMealType)
        val error = vm.uiState.value.saveState as OperationState.Error
        assertEquals("save failed", error.cause.message)
        assertEquals(1, error.cause.suppressed.size)
        assertTrue(vm.uiState.value.records.isEmpty())
    }

    private inner class FakeFoods : FoodRepository {
        var availableFoods = listOf(food)
        var foodCalls = 0
        var sourceCalls = 0
        var failFoods = false
        var failSource = false
        override suspend fun getFoods(): List<Food> {
            foodCalls++
            if (failFoods) throw IOException("foods failed")
            return availableFoods
        }
        override suspend fun getSourceInfo(): SourceInfo {
            sourceCalls++
            if (failSource) throw IOException("source failed")
            return SourceInfo("test", "test.json", emptyList())
        }
    }

    private class FakeMeals : MealRepository {
        val records = MutableStateFlow<List<MealRecord>>(emptyList())
        val attempts = mutableListOf<MealRecord>()
        var initializeCalls = 0
        var observationCalls = 0
        var saveCalls = 0
        var failInitialize = false
        var failSave = false
        var saveGate: CompletableDeferred<Unit>? = null
        override suspend fun initialize() {
            initializeCalls++
            if (failInitialize) throw IOException("read failed")
        }
        override fun observeRecords(): Flow<List<MealRecord>> {
            observationCalls++
            return records
        }
        override suspend fun saveMeal(record: MealRecord) {
            saveCalls++
            attempts.add(record)
            saveGate?.await()
            if (failSave) throw IOException("save failed")
            records.value = records.value.filterNot { it.recordId == record.recordId } + record
        }
    }
}
