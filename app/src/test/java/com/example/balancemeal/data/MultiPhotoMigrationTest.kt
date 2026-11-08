package com.example.balancemeal.data

import com.example.balancemeal.model.*
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MultiPhotoMigrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun record(id: String, day: Int) = MealRecord(id, LocalDate.of(2026, 11, day),
        LocalTime.of(8, 0), MealType.BREAKFAST, items = emptyList())

    @Test fun legacySingleNullAndAbsentMigrateWithoutRewritingUntilAtomicSave() = runBlocking {
        val base = Json { encodeDefaults = true }
        val originals = (1..3).map { record("old-$it", it) }
        val legacy = JsonArray(originals.mapIndexed { index, record ->
            val fields = base.encodeToJsonElement(record).jsonObject - "photoReferences"
            JsonObject(if (index == 2) fields else fields + ("photoReference" to
                if (index == 0) JsonPrimitive("file:///old/photo.img") else JsonNull))
        }).toString()
        val file = File(temporary.root, "records.json").apply { writeText(legacy) }
        val repo = LocalMealRepository(file)
        repo.initialize()
        assertEquals(legacy, file.readText())
        val loaded = repo.observeRecords().first()
        assertEquals(listOf("file:///old/photo.img"), loaded[0].photoReferences)
        assertTrue(loaded[1].photoReferences.isEmpty())
        assertTrue(loaded[2].photoReferences.isEmpty())
        assertEquals(originals.map { it.recordId }, loaded.map { it.recordId })
        repo.saveMeal(loaded[1].copy(time = LocalTime.of(9, 0)))
        assertFalse(file.readText().contains("\"photoReference\":"))
        assertTrue(file.readText().contains("\"photoReferences\":"))
        val restored = LocalMealRepository(file).also { it.initialize() }.observeRecords().first()
        assertEquals(loaded[0], restored[0])
        assertEquals(loaded[2], restored[2])
        assertEquals("old-2", restored[1].recordId)
    }

    @Test fun zeroOneManyAndAllRemovedRoundTripWithoutIncreasingMealCount() = runBlocking {
        val file = File(temporary.root, "records.json")
        var repo = LocalMealRepository(file).also { it.initialize() }
        for (photos in listOf(emptyList(), listOf("file:///one"), listOf("file:///one", "file:///two"), emptyList())) {
            repo.saveMeal(record("original", 8).copy(photoReferences = photos))
            repo = LocalMealRepository(file).also { it.initialize() }
            assertEquals(1, repo.observeRecords().first().size)
            assertEquals("original", repo.observeRecords().first().single().recordId)
            assertEquals(photos, repo.observeRecords().first().single().photoReferences)
        }
    }

    @Test fun malformedLegacyPhotoDoesNotOverwriteOriginal() = runBlocking {
        val file = File(temporary.root, "records.json")
        val json = Json { encodeDefaults = true }
        val fields = json.encodeToJsonElement(record("keep-id", 8)).jsonObject - "photoReferences"
        val original = JsonArray(listOf(JsonObject(fields + ("photoReference" to JsonPrimitive(123))))).toString()
        file.writeText(original)
        try {
            LocalMealRepository(file).initialize()
            fail("Malformed photo reference must fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals(original, file.readText())
    }
}
