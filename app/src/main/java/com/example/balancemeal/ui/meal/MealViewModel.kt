package com.example.balancemeal.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.balancemeal.data.FoodRepository
import com.example.balancemeal.data.MealRepository
import com.example.balancemeal.model.*
import com.example.balancemeal.nutrition.calculateNutrients
import com.example.balancemeal.nutrition.isValidNutrition
import java.time.Clock
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

class MealViewModel(
    private val foodRepository: FoodRepository,
    private val mealRepository: MealRepository,
    private val clock: Clock,
    private val photoStorage: com.example.balancemeal.data.PhotoStorage
) : ViewModel() {
    private val now = LocalDateTime.now(clock)
    private var entryTime = java.time.LocalTime.MIDNIGHT
    private var hasEnteredRecord = false
    private val mutableUiState = MutableStateFlow(
        MealUiState(homeDate = now.toLocalDate(), selectedDate = now.toLocalDate(), draftsByMealType = MealType.entries.associateWith {
            MealDraft(date = now.toLocalDate(), time = defaultMealTime(it, entryTime))
        })
    )
    val uiState = mutableUiState.asStateFlow()

    val analysisState = uiState
        .map { state ->
            val thirtyDaysAgo = java.time.LocalDate.now(clock).minusDays(30)
            val recentRecords = state.records.filter { !it.date.isBefore(thirtyDaysAgo) }

            var totalKcal = 0.0
            val nutrientsMap = mutableMapOf<MealType, Nutrients>()

            for (record in recentRecords) {
                var mealKcal = 0.0
                var mealCarb = 0.0
                var mealProtein = 0.0
                var mealFat = 0.0

                for (item in record.items) {
                    val nut = calculateNutrients(item.food.nutrientsPer100, item.amount)
                    if (nut != null) {
                        mealKcal += nut.energyKcal
                        mealCarb += nut.carbohydrateG
                        mealProtein += nut.proteinG
                        mealFat += nut.fatG
                    }
                }

                totalKcal += mealKcal
                val existing = nutrientsMap[record.mealType] ?: Nutrients(0.0, 0.0, 0.0, 0.0)
                nutrientsMap[record.mealType] = Nutrients(
                    carbohydrateG = existing.carbohydrateG + mealCarb,
                    proteinG = existing.proteinG + mealProtein,
                    fatG = existing.fatG + mealFat,
                    energyKcal = existing.energyKcal + mealKcal
                )
            }

            MealAnalysis(
                totalKcal30Days = totalKcal,
                nutrientsByMealType = nutrientsMap
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MealAnalysis()
        )

    private data class PendingSave(val recordId: String, val meal: MealType, val draft: MealDraft)
    private var pendingSave: PendingSave? = null
    private var pendingDeleteId: String? = null

    init {
        readFoods()
        readRecords()
    }

    fun onEvent(event: MealEvent) {
        when (event) {
            MealEvent.EnterRecord -> enterRecord()
            MealEvent.EnsureRecordInitialized -> if (!hasEnteredRecord) enterRecord()
            MealEvent.RefreshTime -> updateState { it }
            is MealEvent.MoveWeek -> {
                val today = LocalDateTime.now(clock).toLocalDate()
                val next = (uiState.value.displayedWeekStart ?: weekMonday(today)).plusWeeks(event.weeks.toLong())
                if (next <= weekMonday(today)) updateState { it.copy(displayedWeekStart = next) }
            }
            is MealEvent.SelectMeal -> selectKey(uiState.value.selectedDate!!, event.mealType)
            is MealEvent.SetHomeDate -> updateState { it.copy(homeDate = event.date) }
            is MealEvent.SetDate -> selectKey(event.date, uiState.value.currentMeal, showSelectedWeek = true)
            is MealEvent.SetTime -> editDraft { it.copy(time = event.time) }
            is MealEvent.SetPhoto -> {
                if (event.reference != null) editDraft(event.mealType, event.date ?: uiState.value.selectedDate!!) { it.copy(photoReferences = listOf(event.reference)) }
            }
            is MealEvent.AddPhotos -> {
                if (event.references.isNotEmpty()) editDraft(event.mealType, event.date) {
                    it.copy(photoReferences = (it.photoReferences + event.references).distinct())
                }
            }
            is MealEvent.RemovePhoto -> editDraft(event.mealType, event.date) {
                it.copy(photoReferences = it.photoReferences.filterNot { reference -> reference == event.reference })
            }
            MealEvent.AddItem -> {
                val item = MealItemDraft()
                editDraft { it.copy(items = it.items + item) }
            }
            is MealEvent.SelectFood -> {
                val food = uiState.value.foods.find { it.code == event.foodCode } ?: return
                val targetKey = event.date?.let { MealKey(it, event.mealType) }
                    ?: uiState.value.draftsByKey.keys.find { key -> key.mealType == event.mealType &&
                        uiState.value.draftsByKey[key]?.items?.any { it.itemId == event.itemId } == true } ?: return
                val target = uiState.value.draftsByKey[targetKey]?.items
                    ?.find { it.itemId == event.itemId } ?: return
                if (target.food?.code == food.code) return
                editDraft(event.mealType, targetKey.date) { draft -> draft.copy(items = draft.items.map {
                    if (it.itemId == event.itemId) it.copy(food = food, amountInput = "") else it
                }) }
            }
            is MealEvent.SetAmount -> editDraft { draft -> draft.copy(items = draft.items.map {
                if (it.itemId == event.itemId) it.copy(amountInput = event.input) else it
            }) }
            is MealEvent.RemoveItem -> editDraft { draft ->
                draft.copy(items = draft.items.filterNot { it.itemId == event.itemId })
            }
            MealEvent.SaveCurrentMeal -> saveMeal()
            MealEvent.DeleteCurrentMeal -> deleteMeal()
            MealEvent.RetryFoodReading ->
                if (uiState.value.foodReadState is OperationState.Error) readFoods()
            MealEvent.RetryRecordReading ->
                if (uiState.value.recordReadState is OperationState.Error) readRecords()
            MealEvent.RetrySave ->
                if (uiState.value.saveState is OperationState.Error &&
                    uiState.value.foodReadState == OperationState.Success &&
                    uiState.value.recordReadState == OperationState.Success) {
                    pendingSave?.let(::save) ?: pendingDeleteId?.let(::deleteMeal)
                }
            MealEvent.ClearInputErrors -> updateState { it.copy(inputErrors = emptyMap()) }
        }
    }

    private fun updateState(transform: (MealUiState) -> MealUiState) {
        val checkedNow = LocalDateTime.now(clock)
        mutableUiState.update { previous ->
            val changed = transform(previous)
            val date = changed.selectedDate ?: now.toLocalDate()
            val conflicts = changed.records.groupBy { MealKey(it.date, it.mealType) }
                .filterValues { it.size > 1 }.mapValues { it.value.map { record -> record.recordId } }
            val drafts = changed.draftsByKey.toMutableMap()
            MealType.entries.forEach { type ->
                val key = MealKey(date, type)
                if (key !in drafts) drafts[key] = draftFor(changed.records, key)
            }
            val state = changed.copy(
                today = checkedNow.toLocalDate(),
                displayedWeekStart = changed.displayedWeekStart ?: weekMonday(checkedNow.toLocalDate()),
                timeValidationMessage = if (isFutureMeal(date, drafts.getValue(MealKey(date, changed.currentMeal)).time, checkedNow))
                    "미래 시각에는 저장할 수 없습니다. 식사 시간을 수정해 주세요." else null,
                draftsByKey = drafts,
                draftsByMealType = MealType.entries.associateWith { drafts.getValue(MealKey(date, it)) },
                duplicateMealKeys = conflicts,
                editingRecordId = changed.records.singleOrNull {
                    it.date == date && it.mealType == changed.currentMeal
                }?.recordId
            )
            val items = state.draftsByMealType[state.currentMeal]?.items.orEmpty()
            val rows = items.mapNotNull { item ->
                val food = item.food ?: return@mapNotNull null
                val amount = item.amountInput.trim().toDoubleOrNull() ?: return@mapNotNull null
                calculateNutrients(food.nutrientsPer100, amount)?.let { item.itemId to it }
            }.toMap()
            val total = if (items.isNotEmpty() && rows.size == items.size) {
                rows.values.fold(Nutrients(0.0, 0.0, 0.0, 0.0)) { sum, nutrients ->
                    Nutrients(
                        sum.carbohydrateG + nutrients.carbohydrateG,
                        sum.proteinG + nutrients.proteinG,
                        sum.fatG + nutrients.fatG,
                        sum.energyKcal + nutrients.energyKcal
                    )
                }.takeIf { it.isValidNutrition() }
            } else null
            state.copy(nutritionPreview = MealNutritionPreview(rows, total))
        }
    }

    private fun editDraft(
        mealType: MealType = uiState.value.currentMeal,
        date: java.time.LocalDate = uiState.value.selectedDate!!,
        transform: (MealDraft) -> MealDraft
    ) {
        val key = MealKey(date, mealType)
        if (uiState.value.savingMealKey == key) return
        if (uiState.value.saveState is OperationState.Error) {
            pendingSave = null
            pendingDeleteId = null
        }
        updateState { state ->
            val draft = state.draftsByKey[key] ?: draftFor(state.records, key)
            state.copy(
                draftsByKey = state.draftsByKey + (key to transform(draft)),
                dirtyMealKeys = state.dirtyMealKeys + key,
                inputErrors = emptyMap(),
                saveState = if (state.saveState == OperationState.Loading) state.saveState else OperationState.Idle
            )
        }
    }

    private fun draftFor(records: List<MealRecord>, key: MealKey): MealDraft {
        val record = records.singleOrNull { it.date == key.date && it.mealType == key.mealType }
        return record?.let {
            MealDraft(it.date, it.time, it.photoReferences, it.items.map { item ->
                MealItemDraft(item.itemId, item.food, item.amount.toString())
            })
        } ?: MealDraft(key.date, defaultMealTime(key.mealType, entryTime))
    }

    private fun enterRecord() {
        val current = LocalDateTime.now(clock)
        entryTime = current.toLocalTime().withSecond(0).withNano(0)
        val first = !hasEnteredRecord
        hasEnteredRecord = true
        updateState { state ->
            val drafts = state.draftsByKey.mapValues { (key, draft) ->
                val saved = state.records.any { it.date == key.date && it.mealType == key.mealType }
                if (key.mealType == MealType.SNACK_DRINK && key !in state.dirtyMealKeys &&
                    key != state.savingMealKey && !saved && draft.items.isEmpty() && draft.photoReferences.isEmpty())
                    draft.copy(time = entryTime) else draft
            }
            state.copy(
                selectedDate = if (first) current.toLocalDate() else state.selectedDate,
                displayedWeekStart = if (first) weekMonday(current.toLocalDate()) else state.displayedWeekStart,
                draftsByKey = drafts
            )
        }
    }

    private fun selectKey(date: java.time.LocalDate, meal: MealType, showSelectedWeek: Boolean = false) {
        if (date > LocalDateTime.now(clock).toLocalDate()) return
        updateState { it.copy(
            selectedDate = date, currentMeal = meal, inputErrors = emptyMap(),
            displayedWeekStart = if (showSelectedWeek) weekMonday(date) else it.displayedWeekStart,
            saveState = if (it.saveState == OperationState.Loading) it.saveState else OperationState.Idle
        ) }
    }

    private fun readFoods() {
        if (uiState.value.foodReadState == OperationState.Loading ||
            uiState.value.foodReadState == OperationState.Success) return
        updateState { it.copy(foodReadState = OperationState.Loading) }
        viewModelScope.launch {
            try {
                // 출처만 실패했으면 이미 불러온 음식 목록을 다시 요청하지 않습니다.
                if (uiState.value.foods.isEmpty()) {
                    val foods = foodRepository.getFoods()
                    updateState { it.copy(foods = foods) }
                }
                if (uiState.value.sourceInfo == null) {
                    val source = foodRepository.getSourceInfo()
                    updateState { it.copy(sourceInfo = source) }
                }
                updateState { it.copy(foodReadState = OperationState.Success) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateState { it.copy(foodReadState = OperationState.Error(error)) }
            }
        }
    }

    private fun readRecords() {
        if (uiState.value.recordReadState == OperationState.Loading ||
            uiState.value.recordReadState == OperationState.Success) return
        updateState { it.copy(recordReadState = OperationState.Loading) }
        viewModelScope.launch {
            try {
                mealRepository.initialize()
                mealRepository.observeRecords().collect { records ->
                    updateState {
                        it.copy(
                            records = records, recordReadState = OperationState.Success,
                            draftsByKey = it.draftsByKey.mapValues { (key, draft) ->
                                if (key in it.dirtyMealKeys || key == it.savingMealKey ||
                                    it.records.filter { old -> old.date == key.date && old.mealType == key.mealType } ==
                                    records.filter { next -> next.date == key.date && next.mealType == key.mealType }) draft
                                else draftFor(records, key)
                            },
                            inputErrors = it.inputErrors - "records"
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateState { it.copy(recordReadState = OperationState.Error(error)) }
            }
        }
    }

    /** 검증부터 사진 보관과 기록 저장까지 고정된 초안으로 처리합니다.
     * 기존 기록이 있고 항목이 비어 있으면 삭제로 처리합니다. */
    fun saveMeal() {
        val state = uiState.value
        if (state.saveState == OperationState.Loading) return
        val draft = state.draftsByMealType.getValue(state.currentMeal)

        // 기존 기록이 있고 항목을 모두 삭제한 경우 → 기록 삭제
        if (draft.items.isEmpty() && state.editingRecordId != null) {
            deleteMeal(state.editingRecordId)
            return
        }

        val checkedNow = LocalDateTime.now(clock)
        val errors = buildMap {
            if (isFutureMeal(draft.date, draft.time, checkedNow)) put("time", "미래 시각에는 저장할 수 없습니다. 식사 시간을 수정해 주세요.")
            if (state.foodReadState != OperationState.Success) {
                put("foods", "음식 불러오기를 완료한 후 저장해 주세요.")
            }
            if (state.recordReadState != OperationState.Success) {
                put("records", "기록 불러오기를 완료한 후 저장해 주세요.")
            }
            if (MealKey(draft.date, state.currentMeal) in state.duplicateMealKeys) {
                put("records", "동일 날짜·끼니의 중복 기록이 있어 수정할 수 없습니다. 기존 기록은 보존했습니다.")
            }
            if (draft.items.isEmpty()) put("items", "음식을 추가해 주세요.")
            draft.items.forEach { item ->
                val amount = item.amountInput.trim().toDoubleOrNull()
                if (item.food == null) put(item.itemId, "음식을 선택해 주세요.")
                else if (amount == null || !amount.isFinite() || amount <= 0.0) {
                    put(item.itemId, "섭취량은 유한한 0 초과 숫자로 입력해 주세요.")
                } else if (calculateNutrients(item.food.nutrientsPer100, amount) == null) {
                    put(item.itemId, "계산 가능한 범위의 섭취량을 입력해 주세요.")
                }
            }
        }
        updateState { it.copy(inputErrors = errors) }
        if (errors.isNotEmpty()) return
        val pending = PendingSave(
            state.editingRecordId ?: UUID.randomUUID().toString(), state.currentMeal,
            draft.copy(items = draft.items.toList(), photoReferences = draft.photoReferences.toList())
        )
        pendingSave = pending
        save(pending)
    }

    private fun deleteMeal(targetId: String? = uiState.value.editingRecordId) {
        val recordId = targetId ?: return
        val state = uiState.value
        if (state.saveState == OperationState.Loading) return
        val key = MealKey(state.selectedDate!!, state.currentMeal)
        updateState { it.copy(saveState = OperationState.Loading, savingMealType = state.currentMeal, savingMealKey = key) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    mealRepository.deleteMeal(recordId)
                    pendingDeleteId = null
                    pendingSave = null
                    // 삭제 후 해당 키의 초안을 기본값으로 초기화합니다.
                    updateState { s ->
                        s.copy(
                            draftsByKey = s.draftsByKey - key,
                            dirtyMealKeys = s.dirtyMealKeys - key,
                            saveState = OperationState.Success,
                            savingMealType = null,
                            savingMealKey = null,
                            inputErrors = emptyMap()
                        )
                    }
                } catch (error: Exception) {
                    pendingDeleteId = recordId
                    updateState { it.copy(saveState = OperationState.Error(error), savingMealType = null, savingMealKey = null) }
                }
            }
        }
    }

    private fun save(pending: PendingSave) {
        if (uiState.value.saveState == OperationState.Loading) return
        if (isFutureMeal(pending.draft.date, pending.draft.time, LocalDateTime.now(clock))) {
            updateState { it.copy(inputErrors = it.inputErrors + ("time" to "미래 시각에는 저장할 수 없습니다.")) }
            return
        }
        val key = MealKey(pending.draft.date, pending.meal)
        updateState { it.copy(saveState = OperationState.Loading, savingMealType = pending.meal, savingMealKey = key) }
        viewModelScope.launch {
            // 파일 교체가 완료된 뒤 취소가 도착해 성공한 사진을 지우는 일을 방지합니다.
            withContext(NonCancellable) {
                val createdPhotos = mutableListOf<String>()
                var recordSaved = false
                try {
                    val existingPhotos = uiState.value.records.flatMap { it.photoReferences }.toSet()
                    val storedPhotos = pending.draft.photoReferences.distinct().map { source ->
                        if (source.startsWith("file:") && source in existingPhotos) source
                        else photoStorage.copyPhoto(source).also { createdPhotos += it }
                    }
                    val record = MealRecord(
                        recordId = pending.recordId,
                        date = pending.draft.date, time = pending.draft.time, mealType = pending.meal,
                        photoReferences = storedPhotos,
                        items = pending.draft.items.map {
                            MealItem(itemId = it.itemId, food = checkNotNull(it.food),
                                amount = it.amountInput.trim().toDouble())
                        }
                    )
                    mealRepository.saveMeal(record)
                    recordSaved = true
                    updateState { state ->
                        state.copy(
                            draftsByKey = state.draftsByKey + (key to pending.draft.copy(photoReferences = storedPhotos)),
                            // 저장 후에도 세션의 입력 표현과 사진 참조를 늦은 조회 결과로 덮어쓰지 않습니다.
                            dirtyMealKeys = state.dirtyMealKeys + key,
                            saveState = OperationState.Success, savingMealType = null, savingMealKey = null
                        )
                    }
                    pendingSave = null
                } catch (error: Exception) {
                    if (!recordSaved) createdPhotos.forEach { reference ->
                        try { photoStorage.deletePhoto(reference) }
                        catch (cleanupError: Exception) { error.addSuppressed(cleanupError) }
                    }
                    updateState { it.copy(saveState = OperationState.Error(error), savingMealType = null, savingMealKey = null) }
                }
            }
        }
    }
}
