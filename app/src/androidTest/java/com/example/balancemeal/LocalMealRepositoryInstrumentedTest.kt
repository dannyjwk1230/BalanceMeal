package com.example.balancemeal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.balancemeal.data.LocalMealRepository
import com.example.balancemeal.model.MealRecord
import com.example.balancemeal.model.MealType
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalMealRepositoryInstrumentedTest {
    @Test
    fun androidInternalStorageSupportsAtomicReplacementAndReload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "meal-test-").toFile()
        try {
            val file = File(directory, "records.json")
            val repository = LocalMealRepository(file)
            repository.initialize()
            assertTrue(repository.observeRecords().first().isEmpty())
            (1..10).map { id ->
                async {
                    repository.saveMeal(MealRecord(
                        recordId = "test-$id",
                        date = LocalDate.of(2026, 11, 8),
                        time = LocalTime.NOON,
                        mealType = MealType.LUNCH,
                        items = emptyList()
                    ))
                }
            }.awaitAll()
            val loaded = LocalMealRepository(file)
            loaded.initialize()
            assertEquals(10, loaded.observeRecords().first().size)
            assertEquals(repository.observeRecords().first(), loaded.observeRecords().first())
            val application = context.applicationContext as BalanceMealApplication
            assertSame(application.appContainer, application.appContainer)
            assertSame(application.appContainer.mealRepository, application.appContainer.mealRepository)
            assertSame(application.appContainer.foodRepository, application.appContainer.foodRepository)
        } finally {
            directory.deleteRecursively()
        }
    }
}
