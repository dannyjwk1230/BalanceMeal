package com.example.balancemeal.data

import android.content.res.AssetManager
import com.example.balancemeal.model.Food
import com.example.balancemeal.model.SourceInfo
import java.util.Collections
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 앱 범위에서 하나의 인스턴스를 공유하면 검증을 통과한 각 파일을 최초 한 번만 읽습니다.
 * 실패 결과는 캐시하지 않으며 IO/직렬화/검증 예외를 그대로 전달합니다.
 */
class AssetFoodRepository internal constructor(
    private val readAsset: (String) -> String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FoodRepository {
    constructor(
        assets: AssetManager,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        readAsset = { name ->
            assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        },
        ioDispatcher = ioDispatcher
    )

    private val json = Json { ignoreUnknownKeys = false }
    private val foodsMutex = Mutex()
    private val sourceMutex = Mutex()
    private var cachedFoods: List<Food>? = null
    private var cachedSource: SourceInfo? = null

    override suspend fun getFoods(): List<Food> = withContext(ioDispatcher) {
        foodsMutex.withLock {
            cachedFoods ?: json.decodeFromString<List<Food>>(readAsset(FOODS_FILE))
                .also(::validateFoods)
                .let { Collections.unmodifiableList(it.toList()) }
                .also { cachedFoods = it }
        }
    }

    override suspend fun getSourceInfo(): SourceInfo = withContext(ioDispatcher) {
        sourceMutex.withLock {
            cachedSource ?: json.decodeFromString<SourceInfo>(readAsset(SOURCE_FILE))
                .also(::validateSource)
                .let { it.copy(sources = Collections.unmodifiableList(it.sources.toList())) }
                .also { cachedSource = it }
        }
    }

    private fun validateFoods(foods: List<Food>) {
        require(foods.size == 100) { "$FOODS_FILE: 음식은 100개여야 합니다 (실제 ${foods.size}개)." }
        require(foods.map { it.code }.toSet().size == foods.size) {
            "$FOODS_FILE: 중복 식품코드가 있습니다."
        }
        foods.forEachIndexed { index, food ->
            require(food.code.isNotBlank()) { "$FOODS_FILE: [$index] code가 비어 있습니다." }
            require(food.name.isNotBlank()) { "$FOODS_FILE: [${food.code}] name이 비어 있습니다." }
            val nutrients = food.nutrientsPer100
            mapOf(
                "carbohydrateG" to nutrients.carbohydrateG,
                "proteinG" to nutrients.proteinG,
                "fatG" to nutrients.fatG,
                "energyKcal" to nutrients.energyKcal
            ).forEach { (field, value) ->
                require(value.isFinite() && value >= 0.0) {
                    "$FOODS_FILE: [${food.code}] $field 값은 유한한 0 이상이어야 합니다."
                }
            }
        }
        // 필수 필드, 타입, G/ML 외 단위는 기본값 없는 P02 모델의 엄격한 역직렬화로 검증합니다.
    }

    private fun validateSource(info: SourceInfo) {
        require(info.datasetName.isNotBlank()) { "$SOURCE_FILE: datasetName이 비어 있습니다." }
        require(info.originalFileName.isNotBlank()) { "$SOURCE_FILE: originalFileName이 비어 있습니다." }
        require(info.sources.isNotEmpty()) { "$SOURCE_FILE: sources가 비어 있습니다." }
        info.sources.forEachIndexed { index, source ->
            require(source.sourceName.isNotBlank() && source.providerName.isNotBlank()) {
                "$SOURCE_FILE: [$index] 출처명과 제공기관은 필수입니다."
            }
        }
    }

    companion object {
        const val FOODS_FILE = "foods_sample.json"
        const val SOURCE_FILE = "nutrition_source.json"
    }
}
