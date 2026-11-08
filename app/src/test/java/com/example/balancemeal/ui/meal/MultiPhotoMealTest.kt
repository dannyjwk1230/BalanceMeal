package com.example.balancemeal.ui.meal

import androidx.lifecycle.ViewModelStore
import com.example.balancemeal.data.*
import com.example.balancemeal.model.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.*
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class MultiPhotoMealTest {
    @get:Rule val temporary = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val date = LocalDate.of(2026, 11, 8)
    private val clock = Clock.fixed(Instant.parse("2026-11-08T04:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val food = Food("001", "테스트", AmountUnit.G, Nutrients(1.0, 2.0, 3.0, 39.0))
    private val foods = object : FoodRepository {
        override suspend fun getFoods() = listOf(food)
        override suspend fun getSourceInfo() = SourceInfo("test", "test.json", emptyList())
    }
    private val file get() = File(temporary.root, "records.json")
    private val images get() = File(temporary.root, "images")
    private var unavailableSources = false
    private val photos get() = FilePhotoStorage(images, { source ->
        if (unavailableSources || source == "content://bad") throw IOException("Cannot read source")
        ByteArrayInputStream(source.toByteArray())
    }, dispatcher)

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun vm() = MealViewModel(foods, LocalMealRepository(file, dispatcher), clock, photos)
        .also { store.put("meal", it) }
    private fun input(vm: MealViewModel) {
        vm.onEvent(MealEvent.AddItem)
        val id = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().itemId
        vm.onEvent(MealEvent.SelectFood(id, food.code, MealType.BREAKFAST, date))
        vm.onEvent(MealEvent.SetAmount(id, "100"))
    }
    private fun draft(vm: MealViewModel) = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST)
    private fun add(vm: MealViewModel, vararg refs: String) =
        vm.onEvent(MealEvent.AddPhotos(refs.toList(), MealType.BREAKFAST, date))

    @Test fun zeroOneManyRemoveAllAndRestartRetainIdCountAndInternalFiles() = runTest(dispatcher) {
        var vm = vm()
        runCurrent()
        input(vm)
        vm.saveMeal()
        vm.saveMeal()
        runCurrent()
        assertEquals(OperationState.Success, vm.uiState.value.saveState)
        assertTrue(vm.uiState.value.records.single().photoReferences.isEmpty())
        val recordId = vm.uiState.value.records.single().recordId
        vm = vm()
        runCurrent()
        assertEquals(recordId, vm.uiState.value.editingRecordId)
        assertTrue(draft(vm).photoReferences.isEmpty())
        assertEquals("100.0", draft(vm).items.single().amountInput)

        add(vm, "content://one", "content://one")
        assertEquals(listOf("content://one"), draft(vm).photoReferences)
        vm.saveMeal()
        runCurrent()
        val first = draft(vm).photoReferences.single()
        assertEquals("content://one", File(URI(first)).readText())
        vm = vm()
        runCurrent()
        assertEquals(listOf(first), draft(vm).photoReferences)
        add(vm, "content://two", "content://three", "content://two")
        val beforeCancel = draft(vm)
        add(vm) // Picker cancellation gives an empty result.
        assertEquals(beforeCancel, draft(vm))
        assertEquals(3, draft(vm).photoReferences.size)
        vm.saveMeal()
        runCurrent()
        val stored = draft(vm).photoReferences
        assertEquals(first, stored.first())
        assertEquals(3, images.listFiles()!!.size)
        assertEquals(listOf("content://one", "content://two", "content://three"),
            stored.map { File(URI(it)).readText() })
        vm.saveMeal()
        runCurrent()
        assertEquals(3, images.listFiles()!!.size)
        assertEquals(recordId, vm.uiState.value.records.single().recordId)

        unavailableSources = true
        vm = vm()
        runCurrent()
        assertEquals(stored, draft(vm).photoReferences)
        assertTrue(stored.all { File(URI(it)).readBytes().isNotEmpty() })
        vm.onEvent(MealEvent.RemovePhoto(stored[1], MealType.BREAKFAST, date))
        assertEquals(listOf(stored[0], stored[2]), draft(vm).photoReferences)
        vm.saveMeal()
        runCurrent()
        vm = vm()
        runCurrent()
        assertEquals(listOf(stored[0], stored[2]), draft(vm).photoReferences)
        draft(vm).photoReferences.forEach {
            vm.onEvent(MealEvent.RemovePhoto(it, MealType.BREAKFAST, date))
        }
        vm.saveMeal()
        runCurrent()
        vm = vm()
        runCurrent()
        assertEquals(recordId, vm.uiState.value.records.single().recordId)
        assertEquals(1, vm.uiState.value.records.size)
        assertTrue(draft(vm).photoReferences.isEmpty())
        // Removing an attachment does not delete potentially shared legacy files.
        assertTrue(stored.all { File(URI(it)).exists() })
        unavailableSources = true
        val restored = vm()
        runCurrent()
        assertTrue(restored.uiState.value.records.single().photoReferences.isEmpty())
    }

    @Test fun pickerTargetAndPhotoListsAreIsolatedByDateAndMeal() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        val otherDate = date.minusDays(1)
        vm.onEvent(MealEvent.SetDate(otherDate))
        vm.onEvent(MealEvent.SelectMeal(MealType.LUNCH))
        add(vm, "content://late-result", "content://late-result")
        assertTrue(vm.uiState.value.draftsByMealType.getValue(MealType.LUNCH).photoReferences.isEmpty())
        vm.onEvent(MealEvent.SetDate(date))
        vm.onEvent(MealEvent.SelectMeal(MealType.BREAKFAST))
        assertEquals(listOf("content://late-result"), draft(vm).photoReferences)
        vm.onEvent(MealEvent.SetDate(otherDate))
        assertTrue(draft(vm).photoReferences.isEmpty())
        vm.onEvent(MealEvent.AddPhotos(listOf("content://yesterday"), MealType.BREAKFAST, otherDate))
        vm.onEvent(MealEvent.SetDate(date))
        assertEquals(listOf("content://late-result"), draft(vm).photoReferences)
    }

    @Test fun partialCopyFailureCleansOnlyNewPhotosAndKeepsAllInputs() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        input(vm)
        add(vm, "content://one")
        vm.saveMeal()
        runCurrent()
        val saved = vm.uiState.value.records.single()
        add(vm, "content://two", "content://bad")
        val before = draft(vm)
        vm.saveMeal()
        runCurrent()
        assertTrue(vm.uiState.value.saveState is OperationState.Error)
        assertEquals(before, draft(vm))
        assertEquals(saved, vm.uiState.value.records.single())
        assertEquals(1, images.listFiles()!!.size)
        assertTrue(File(URI(saved.photoReferences.single())).exists())
        assertEquals(1, LocalMealRepository(file, dispatcher).also { it.initialize() }
            .observeRecords().first().size)
    }
}
