package com.example.balancemeal.data

import com.example.balancemeal.model.*
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalMealRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun file() = File(temporary.root, "meal_records.json")
    private fun record(id: String = "record-1") = MealRecord(
        recordId = id,
        date = LocalDate.of(2026, 11, 8),
        time = LocalTime.of(12, 30),
        mealType = MealType.LUNCH,
        photoReference = "content://test/photo",
        items = listOf(MealItem("item-1", Food("001", "테스트", AmountUnit.G,
            Nutrients(1.0, 2.0, 3.0, 39.0)), 100.0))
    )

    @Test
    fun noEmissionOrSavingBeforeInitializationThenMissingFileIsEmpty() = runBlocking {
        val file = file()
        val repository = LocalMealRepository(file)
        assertNull(withTimeoutOrNull(50) { repository.observeRecords().first() })
        try {
            repository.saveMeal(record())
            fail("Save must require successful initialization")
        } catch (_: IllegalStateException) { }
        assertFalse(file.exists())
        repository.initialize()
        assertEquals(emptyList<MealRecord>(), repository.observeRecords().first())
        assertFalse(file.exists())
    }

    @Test
    fun corruptFilePropagatesErrorAndCannotBeOverwritten() = runBlocking {
        val file = file()
        val corrupt = "{ broken JSON"
        file.writeText(corrupt)
        val repository = LocalMealRepository(file)
        try {
            repository.initialize()
            fail("Corrupt file must fail")
        } catch (_: IllegalArgumentException) { }
        try {
            repository.saveMeal(record())
            fail("Save must remain blocked")
        } catch (_: IllegalStateException) { }
        assertEquals(corrupt, file.readText())
        assertNull(withTimeoutOrNull(50) { repository.observeRecords().first() })
        // Explicit external repair permits a later initialization attempt.
        file.writeText("[]")
        repository.initialize()
        assertTrue(repository.observeRecords().first().isEmpty())
    }

    @Test
    fun saveAndReloadPreserveRecordAndSameIdUpdatesInsteadOfDuplicating() = runBlocking {
        val file = file()
        val repository = LocalMealRepository(file)
        repository.initialize()
        repository.saveMeal(record())
        val updated = record().copy(time = LocalTime.of(18, 15))
        repository.saveMeal(updated)
        val reloaded = LocalMealRepository(file)
        reloaded.initialize()
        assertEquals(listOf(updated), reloaded.observeRecords().first())
        assertEquals(listOf(updated), repository.observeRecords().first())
        assertTrue(file.readText().contains("2026-11-08"))
        assertTrue(temporary.root.listFiles()!!.none { it.extension == "tmp" })
    }

    @Test
    fun concurrentSavesLoseNoRecordsAndMatchFile() = runBlocking {
        val file = file()
        val repository = LocalMealRepository(file)
        repository.initialize()
        (1..40).map { id -> async { repository.saveMeal(record("record-$id").copy(date = LocalDate.of(2026, 11, 8).plusDays(id.toLong()))) } }.awaitAll()
        val observed = repository.observeRecords().first()
        assertEquals(40, observed.size)
        assertEquals(40, observed.map { it.recordId }.toSet().size)
        val reloaded = LocalMealRepository(file)
        reloaded.initialize()
        assertEquals(observed, reloaded.observeRecords().first())
    }

    @Test
    fun failedSerializationLeavesPreviousFileAndFlowUntouched() = runBlocking {
        val file = file()
        val repository = LocalMealRepository(file)
        repository.initialize()
        val valid = record()
        repository.saveMeal(valid)
        val originalBytes = file.readBytes()
        val invalid = record("record-2").let {
            it.copy(items = listOf(it.items.single().copy(amount = Double.NaN)))
        }
        try {
            repository.saveMeal(invalid)
            fail("Invalid serialization must fail")
        } catch (_: IllegalArgumentException) { }
        assertArrayEquals(originalBytes, file.readBytes())
        assertEquals(listOf(valid), repository.observeRecords().first())
    }

    @Test
    fun failedAtomicReplacementDoesNotPublishAndCleansTemporaryFile() = runBlocking {
        val file = file()
        val repository = LocalMealRepository(file)
        repository.initialize()
        assertTrue(file.mkdir())
        val marker = File(file, "marker").apply { writeText("keep") }
        try {
            repository.saveMeal(record())
            fail("Replacing a nonempty directory must fail")
        } catch (_: IOException) { }
        assertEquals("keep", marker.readText())
        assertTrue(repository.observeRecords().first().isEmpty())
        assertTrue(temporary.root.listFiles()!!.none { it.extension == "tmp" })
    }

    @Test fun sameDateAndMealUpsertsAtomicallyAndKeepsFirstIdAcrossReloads() = runBlocking {
        val file = file()
        val repo = LocalMealRepository(file)
        repo.initialize()
        repo.saveMeal(record("original"))
        (1..40).map { n -> async {
            repo.saveMeal(record("new-$n").copy(time = LocalTime.of(14, n % 60)))
        } }.awaitAll()
        assertEquals(1, repo.observeRecords().first().size)
        assertEquals("original", repo.observeRecords().first().single().recordId)
        val reloaded = LocalMealRepository(file)
        reloaded.initialize()
        repeat(3) { reloaded.saveMeal(record("another").copy(time = LocalTime.of(18, 30))) }
        assertEquals("original", reloaded.observeRecords().first().single().recordId)
        assertEquals(1, reloaded.observeRecords().first().size)
        val before = file.readBytes()
        try {
            reloaded.saveMeal(record("original").copy(date = LocalDate.of(2026, 11, 9)))
            fail("Moving the old ID must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun legacyDuplicateMealsAreReportedWithoutDeletingMergingOrRewriting() = runBlocking {
        val file = file()
        val legacy = listOf(record("legacy-a"), record("legacy-b"))
        file.writeText(Json.encodeToString(legacy))
        val bytes = file.readBytes()
        val repo = LocalMealRepository(file)
        repo.initialize()
        assertEquals(legacy, repo.observeRecords().first())
        try {
            repo.saveMeal(record("new"))
            fail("Ambiguous duplicates must not be overwritten")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("legacy-a"))
            assertTrue(error.message!!.contains("legacy-b"))
        }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(legacy, repo.observeRecords().first())
    }

    @Test
    fun directoryReadErrorIsNotTreatedAsMissingFile() = runBlocking {
        val file = file()
        assertTrue(file.mkdir())
        val repository = LocalMealRepository(file)
        try {
            repository.initialize()
            fail("Read failure must propagate")
        } catch (_: IOException) { }
        assertNull(withTimeoutOrNull(50) { repository.observeRecords().first() })
    }

    @Test
    fun missingPersistedIdDoesNotGenerateReplacementIdentity() = runBlocking {
        val file = file()
        val text = Json.encodeToString(listOf(record())).replace("\"recordId\":\"record-1\",", "")
        file.writeText(text)
        val repository = LocalMealRepository(file)
        try {
            repository.initialize()
            fail("Missing ID must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(text, file.readText())
    }

    @Test
    fun inputListMutationCannotChangePublishedOrPersistedRecords() = runBlocking {
        val file = file()
        val repository = LocalMealRepository(file)
        repository.initialize()
        val meal = record()
        val mutableItems = meal.items.toMutableList()
        repository.saveMeal(meal.copy(items = mutableItems))
        mutableItems.clear()
        assertEquals(listOf(meal), repository.observeRecords().first())
        val reloaded = LocalMealRepository(file)
        reloaded.initialize()
        assertEquals(listOf(meal), withTimeout(1000) { reloaded.observeRecords().first() })
    }

    @Test
    fun deleteMealRemovesPersistedRecordAndEmitsUpdatedList() = runBlocking {
        val file = file()
        val repository = LocalMealRepository(file)
        repository.initialize()
        val meal = record()
        repository.saveMeal(meal)
        assertEquals(listOf(meal), repository.observeRecords().first())

        repository.deleteMeal(meal.recordId)
        assertEquals(emptyList<MealRecord>(), repository.observeRecords().first())

        val reloaded = LocalMealRepository(file)
        reloaded.initialize()
        assertEquals(emptyList<MealRecord>(), withTimeout(1000) { reloaded.observeRecords().first() })
    }

    @Test
    fun deleteNonExistentRecordSilentlySucceeds() = runBlocking {
        val file = file()
        val repository = LocalMealRepository(file)
        repository.initialize()
        val meal = record()
        repository.saveMeal(meal)

        repository.deleteMeal("non-existent-id")
        assertEquals(listOf(meal), repository.observeRecords().first())
    }
}
