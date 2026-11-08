package com.example.balancemeal.data

import com.example.balancemeal.model.AmountUnit
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.*
import org.junit.Test

class AssetFoodRepositoryTest {
    private fun asset(name: String) = File("src/main/assets", name).readText(Charsets.UTF_8)
    private fun repository() = AssetFoodRepository(readAsset = ::asset)

    @Test
    fun realAssetsMatchOriginalsAndLoadWithoutChangingValues() = runBlocking {
        listOf(AssetFoodRepository.FOODS_FILE, AssetFoodRepository.SOURCE_FILE).forEach { name ->
            // IDE가 Windows 줄바꿈으로 저장할 수 있으므로 줄바꿈만 정규화합니다.
            assertEquals(
                File("src/main/java/com/example/balancemeal/sampledata", name)
                    .readText(Charsets.UTF_8).replace("\r\n", "\n"),
                asset(name).replace("\r\n", "\n")
            )
        }
        val repository = repository()
        val foods = repository.getFoods()
        assertEquals(100, foods.size)
        assertEquals(100, foods.map { it.code }.toSet().size)
        assertEquals(80, foods.count { it.unit == AmountUnit.G })
        assertEquals(20, foods.count { it.unit == AmountUnit.ML })
        val rice = foods.single { it.code == "D301-022000000-0001" }
        assertEquals("쌀밥", rice.name)
        assertEquals(37.33, rice.nutrientsPer100.carbohydrateG, 0.0)
        assertEquals(166.0, rice.nutrientsPer100.energyKcal, 0.0)
        val source = repository.getSourceInfo()
        assertEquals("전국통합식품영양성분정보_음식_표준데이터", source.datasetName)
        assertEquals(2, source.sources.size)
        assertTrue(source.sources.all { it.referenceDate == LocalDate.of(2026, 9, 29) })
    }

    @Test
    fun concurrentCallsReadEachSuccessfulAssetOnceOnIoThread() = runBlocking {
        val caller = Thread.currentThread()
        val reads = ConcurrentHashMap<String, AtomicInteger>()
        val repository = AssetFoodRepository(readAsset = { name ->
            assertNotSame(caller, Thread.currentThread())
            reads.computeIfAbsent(name) { AtomicInteger() }.incrementAndGet()
            asset(name)
        })
        val foods = (1..20).map { async { repository.getFoods() } }.awaitAll()
        val sources = (1..20).map { async { repository.getSourceInfo() } }.awaitAll()
        assertTrue(foods.all { it === foods.first() })
        assertTrue(sources.all { it === sources.first() })
        assertSame(foods.first(), repository.getFoods())
        assertSame(sources.first(), repository.getSourceInfo())
        assertEquals(1, reads.getValue(AssetFoodRepository.FOODS_FILE).get())
        assertEquals(1, reads.getValue(AssetFoodRepository.SOURCE_FILE).get())
    }

    @Test
    fun malformedFoodsAreRejectedWithoutFallback() = runBlocking {
        val valid = asset(AssetFoodRepository.FOODS_FILE)
        val entries = Json.parseToJsonElement(valid) as JsonArray
        val duplicate = JsonArray(entries.toMutableList().also { it[1] = it[0] }).toString()
        val invalidFiles = listOf(
            "{",
            "[]",
            JsonArray(entries.dropLast(1)).toString(),
            duplicate,
            valid.replaceFirst("\"G\"", "\"KG\""),
            valid.replaceFirst("\"name\": \"쌀밥\"", "\"name\": \" \""),
            valid.replaceFirst("\"code\": \"D301-022000000-0001\"", "\"code\": \"\""),
            valid.replaceFirst("\"carbohydrateG\": 37.33,", ""),
            valid.replaceFirst("37.33", "-1.0"),
            valid.replaceFirst("37.33", "1e999"),
            valid.replaceFirst("37.33", "NaN"),
            valid.replaceFirst("\"unit\": \"G\"", "\"unit\": null")
        )
        invalidFiles.forEachIndexed { index, contents ->
            val repository = AssetFoodRepository(readAsset = { contents })
            try {
                repository.getFoods()
                fail("Invalid file $index was accepted")
            } catch (expected: IllegalArgumentException) {
                // Validation or serialization failure is delivered to the caller.
            }
        }
    }

    @Test
    fun ioFailureIsPropagatedAndNextCallRetries() = runBlocking {
        val failure = IOException("test read failure")
        var reads = 0
        val repository = AssetFoodRepository(readAsset = { name ->
            reads += 1
            if (reads == 1) throw failure
            asset(name)
        })
        try {
            repository.getFoods()
            fail("Expected original IO exception")
        } catch (actual: IOException) {
            // 코루틴 디버그 스택 복원 시 예외가 원본을 cause로 갖는 복사본일 수 있습니다.
            assertEquals(failure.message, actual.message)
            assertTrue(actual === failure || actual.cause === failure)
        }
        assertEquals(100, repository.getFoods().size)
        repository.getFoods()
        assertEquals(2, reads)
    }

    @Test
    fun malformedSourceIsNotCachedAndCanBeRetried() = runBlocking {
        var reads = 0
        val repository = AssetFoodRepository(readAsset = { name ->
            reads += 1
            if (reads == 1) asset(name).replace("2026-09-29", "2026-02-30") else asset(name)
        })
        try {
            repository.getSourceInfo()
            fail("Expected invalid date rejection")
        } catch (expected: IllegalArgumentException) {
            // The shared ISO date serializer rejects impossible dates.
        }
        assertEquals(2, repository.getSourceInfo().sources.size)
        repository.getSourceInfo()
        assertEquals(2, reads)
    }
}
