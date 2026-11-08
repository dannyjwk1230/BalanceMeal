package com.example.balancemeal.data

import com.example.balancemeal.model.MealRecord
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Collections
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.*

class LocalMealRepository(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MealRepository {
    private val json = Json { encodeDefaults = true }
    private val mutex = Mutex()
    private val records = MutableStateFlow<List<MealRecord>?>(null)
    private val observedRecords = records.filterNotNull()

    override fun observeRecords(): Flow<List<MealRecord>> = observedRecords

    override suspend fun initialize(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            if (records.value != null) return@withLock
            val text = try {
                // exists()는 권한 오류도 false로 숨길 수 있으므로 파일 없음만 처리합니다.
                Files.readAllBytes(file.toPath()).toString(Charsets.UTF_8)
            } catch (_: NoSuchFileException) {
                null
            }
            val loaded = if (text == null) emptyList() else decodeRecords(text)
            currentCoroutineContext().ensureActive()
            records.value = snapshot(loaded)
        }
    }

    override suspend fun saveMeal(record: MealRecord): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val previous = checkNotNull(records.value) { "기록 초기화 성공 후에만 저장할 수 있습니다." }
            val matches = previous.filter { it.date == record.date && it.mealType == record.mealType }
            check(matches.size <= 1) {
                "동일 날짜·끼니 중복 기록 ${matches.size}건: ${record.date} ${record.mealType} / " +
                    matches.joinToString { it.recordId } + ". 기존 데이터는 변경하지 않았습니다."
            }
            val sameId = previous.find { it.recordId == record.recordId }
            require(sameId == null || (sameId.date == record.date && sameId.mealType == record.mealType)) {
                "기존 기록의 날짜·끼니를 이동할 수 없습니다."
            }
            val saved = record.copy(
                recordId = matches.singleOrNull()?.recordId ?: record.recordId,
                photoReferences = Collections.unmodifiableList(record.photoReferences.toList()),
                items = Collections.unmodifiableList(record.items.toList())
            )
            val index = previous.indexOfFirst { it.recordId == saved.recordId }
            val next = if (index < 0) previous + saved else previous.mapIndexed { i, old ->
                if (i == index) saved else old
            }
            validateRecords(next)
            val published = snapshot(next)
            val content = json.encodeToString(published)
            currentCoroutineContext().ensureActive()
            // 이 구간에는 suspend 지점이 없습니다. 교체 완료 후 같은 잠금 안에서 방출합니다.
            writeAtomically(content)
            records.value = published
        }
    }

    override suspend fun deleteMeal(recordId: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val previous = checkNotNull(records.value) { "기록 초기화 성공 후에만 삭제할 수 있습니다." }
            val next = previous.filter { it.recordId != recordId }
            if (next.size == previous.size) return@withLock  // 없는 ID는 조용히 성공
            val published = snapshot(next)
            val content = json.encodeToString(published)
            currentCoroutineContext().ensureActive()
            writeAtomically(content)
            records.value = published
        }
    }

    private fun decodeRecords(text: String): List<MealRecord> {
        // 모델의 UUID 기본값으로 손상된 파일의 누락 ID를 자동 생성하지 않습니다.
        val migrated = JsonArray(json.parseToJsonElement(text).jsonArray.map { entry ->
            val fields = entry.jsonObject
            require("recordId" in fields) { "기록 파일에 recordId가 누락되었습니다." }
            fields.getValue("items").jsonArray.forEach { item ->
                require("itemId" in item.jsonObject) { "기록 파일에 itemId가 누락되었습니다." }
            }
            // 읽을 때만 변환합니다. 초기화는 기존 파일을 덮어쓰지 않습니다.
            val legacy = fields["photoReference"]
            require(legacy == null || legacy is JsonNull || (legacy is JsonPrimitive && legacy.isString)) {
                "이전 사진 참조 형식이 잘못되었습니다."
            }
            val references = fields["photoReferences"]?.jsonArray.orEmpty() +
                listOfNotNull(legacy?.takeUnless { it is JsonNull })
            JsonObject(fields - "photoReference" + ("photoReferences" to JsonArray(references.distinct())))
        })
        return json.decodeFromJsonElement<List<MealRecord>>(migrated).also(::validateRecords)
    }

    private fun validateRecords(list: List<MealRecord>) {
        require(list.all { it.recordId.isNotBlank() }) { "recordId는 비어 있을 수 없습니다." }
        require(list.map { it.recordId }.toSet().size == list.size) { "recordId가 중복되었습니다." }
        list.forEach { record ->
            require(record.photoReferences.all { it.isNotBlank() }) { "사진 참조는 비어 있을 수 없습니다." }
            require(record.items.all { it.itemId.isNotBlank() }) { "itemId는 비어 있을 수 없습니다." }
            require(record.items.map { it.itemId }.toSet().size == record.items.size) {
                "한 기록에 중복된 itemId가 있습니다."
            }
        }
    }

    private fun snapshot(list: List<MealRecord>): List<MealRecord> =
        Collections.unmodifiableList(list.map {
            it.copy(photoReferences = Collections.unmodifiableList(it.photoReferences.toList()),
                items = Collections.unmodifiableList(it.items.toList()))
        })

    private fun writeAtomically(content: String) {
        val directory = checkNotNull(file.absoluteFile.parentFile)
        Files.createDirectories(directory.toPath())
        val temporary = File.createTempFile("meal-records-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            // 같은 디렉터리에서 원자적으로 교체합니다. 지원되지 않으면 실패시키고 원본을 유지합니다.
            Files.move(temporary.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }
}
