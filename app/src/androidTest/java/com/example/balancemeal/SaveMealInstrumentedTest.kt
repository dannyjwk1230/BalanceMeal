package com.example.balancemeal

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.balancemeal.data.*
import com.example.balancemeal.model.MealType
import com.example.balancemeal.ui.meal.*
import com.example.balancemeal.ui.screens.record.RecordScreen
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SaveMealInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun saveButtonValidatesThenCopiesPhotoAndPersistsWholeRecord() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "meal-save-test-").toFile()
        val store = ViewModelStore()
        try {
            val source = File(directory, "selected.png")
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val recordsFile = File(directory, "records.json")
            lateinit var vm: MealViewModel
            instrumentation.runOnMainSync {
                vm = MealViewModel(
                    AssetFoodRepository(context.assets),
                    LocalMealRepository(recordsFile),
                    Clock.fixed(Instant.parse("2026-11-08T03:30:00Z"), ZoneId.of("Asia/Seoul")),
                    FilePhotoStorage(File(directory, "images"), context.contentResolver)
                )
                store.put("meal", vm)
            }
            compose.setContent {
                val state by vm.uiState.collectAsStateWithLifecycle()
                MaterialTheme { RecordScreen(state, vm::onEvent) { } }
            }
            compose.waitUntil(10_000) {
                vm.uiState.value.foodReadState == OperationState.Success &&
                    vm.uiState.value.recordReadState == OperationState.Success
            }
            compose.onNodeWithText("식사 저장").performScrollTo().performClick()
            assertTrue("photo" in vm.uiState.value.inputErrors)
            assertTrue("items" in vm.uiState.value.inputErrors)
            assertFalse(recordsFile.exists())

            compose.runOnIdle {
                vm.onEvent(MealEvent.SetPhoto(Uri.fromFile(source).toString(), MealType.BREAKFAST))
                vm.onEvent(MealEvent.AddItem)
                val id = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().itemId
                vm.onEvent(MealEvent.SelectFood(id, vm.uiState.value.foods.first().code, MealType.BREAKFAST))
                vm.onEvent(MealEvent.SetAmount(id, "125.5"))
                vm.onEvent(MealEvent.SelectMeal(MealType.LUNCH))
                vm.onEvent(MealEvent.AddItem)
                vm.onEvent(MealEvent.SelectMeal(MealType.BREAKFAST))
            }
            val before = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST)
            val other = vm.uiState.value.draftsByMealType.getValue(MealType.LUNCH)
            compose.onNodeWithText("식사 저장").performScrollTo().performClick()
            compose.waitUntil(10_000) { vm.uiState.value.saveState == OperationState.Success }
            compose.onNodeWithText("식사를 저장했습니다.").performScrollTo().assertIsDisplayed()
            val reloaded = runBlocking {
                LocalMealRepository(recordsFile).also { it.initialize() }.observeRecords().first()
            }
            assertEquals(1, reloaded.size)
            val record = reloaded.single()
            assertEquals(125.5, record.items.single().amount, 0.0)
            assertTrue(File(URI(record.photoReference!!)).exists())
            assertNotEquals(Uri.fromFile(source).toString(), record.photoReference)
            assertTrue(source.exists())
            val after = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST)
            assertEquals(before.date, after.date)
            assertEquals(before.time, after.time)
            assertNull(after.photoReference)
            assertTrue(after.items.isEmpty())
            assertEquals(other, vm.uiState.value.draftsByMealType.getValue(MealType.LUNCH))
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            directory.deleteRecursively()
        }
    }
}
