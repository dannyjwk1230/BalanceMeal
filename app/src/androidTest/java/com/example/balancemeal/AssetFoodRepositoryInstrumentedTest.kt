package com.example.balancemeal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.balancemeal.data.AssetFoodRepository
import com.example.balancemeal.model.AmountUnit
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** APK에 패키징된 assets를 Android AssetManager로 실제 읽는 테스트입니다. */
@RunWith(AndroidJUnit4::class)
class AssetFoodRepositoryInstrumentedTest {
    @Test
    fun packagedAssetsLoadFoodsAndSources() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AssetFoodRepository(context.assets)
        val foods = repository.getFoods()
        val source = repository.getSourceInfo()
        assertEquals(100, foods.size)
        assertEquals(100, foods.map { it.code }.toSet().size)
        assertEquals(80, foods.count { it.unit == AmountUnit.G })
        assertEquals(20, foods.count { it.unit == AmountUnit.ML })
        val rice = foods.single { it.code == "D301-022000000-0001" }
        assertEquals(166.0, rice.nutrientsPer100.energyKcal, 0.0)
        assertEquals("전국통합식품영양성분정보_음식_표준데이터", source.datasetName)
        assertEquals(2, source.sources.size)
        assertTrue(source.sources.all { it.referenceDate == LocalDate.of(2026, 9, 29) })
        assertSame(foods, repository.getFoods())
        assertSame(source, repository.getSourceInfo())
    }
}
