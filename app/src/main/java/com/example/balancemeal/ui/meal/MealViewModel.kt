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
import kotlinx.coroutines.flow.asStateFlow
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
    private val mutableUiState = MutableStateFlow(
        MealUiState(homeDate = now.toLocalDate(), draftsByMealType = MealType.entries.associateWith {
            MealDraft(date = now.toLocalDate(), time = now.toLocalTime())
        })
    )
    val uiState = mutableUiState.asStateFlow()

    private data class PendingSave(val recordId: String, val meal: MealType, val draft: MealDraft)
    private var pendingSave: PendingSave? = null

    init {
        readFoods()
        readRecords()
    }

    fun onEvent(event: MealEvent) {
        when (event) {
            is MealEvent.SelectMeal -> updateState {
                it.copy(currentMeal = event.mealType, inputErrors = emptyMap())
            }
            is MealEvent.SetHomeDate -> updateState { it.copy(homeDate = event.date) }
            is MealEvent.SetDate -> editDraft { it.copy(date = event.date) }
            is MealEvent.SetTime -> editDraft { it.copy(time = event.time) }
            is MealEvent.SetPhoto -> {
                if (event.reference != null) editDraft(event.mealType) { it.copy(photoReference = event.reference) }
            }
            MealEvent.AddItem -> {
                val item = MealItemDraft()
                editDraft { it.copy(items = it.items + item) }
            }
            is MealEvent.SelectFood -> {
                val food = uiState.value.foods.find { it.code == event.foodCode } ?: return
                val target = uiState.value.draftsByMealType[event.mealType]?.items
                    ?.find { it.itemId == event.itemId } ?: return
                if (target.food?.code == food.code) return
                editDraft(event.mealType) { draft -> draft.copy(items = draft.items.map {
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
            MealEvent.RetryFoodReading ->
                if (uiState.value.foodReadState is OperationState.Error) readFoods()
            MealEvent.RetryRecordReading ->
                if (uiState.value.recordReadState is OperationState.Error) readRecords()
            MealEvent.RetrySave ->
                if (uiState.value.saveState is OperationState.Error &&
                    uiState.value.foodReadState == OperationState.Success &&
                    uiState.value.recordReadState == OperationState.Success) pendingSave?.let(::save)
            MealEvent.ClearInputErrors -> updateState { it.copy(inputErrors = emptyMap()) }
        }
    }

    private fun updateState(transform: (MealUiState) -> MealUiState) {
        mutableUiState.update { previous ->
            val state = transform(previous)
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
        transform: (MealDraft) -> MealDraft
    ) {
        if (uiState.value.savingMealType == mealType) return
        if (uiState.value.saveState is OperationState.Error) pendingSave = null
        updateState { state ->
            val draft = state.draftsByMealType.getValue(mealType)
            state.copy(
                draftsByMealType = state.draftsByMealType + (mealType to transform(draft)),
                inputErrors = emptyMap(),
                saveState = if (state.saveState is OperationState.Error) OperationState.Idle else state.saveState
            )
        }
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

    /** 검증부터 사진 보관과 기록 저장까지 고정된 초안으로 처리합니다. */
    fun saveMeal() {
        val state = uiState.value
        if (state.saveState == OperationState.Loading) return
        val draft = state.draftsByMealType.getValue(state.currentMeal)
        val errors = buildMap {
            if (state.foodReadState != OperationState.Success) {
                put("foods", "음식 불러오기를 완료한 후 저장해 주세요.")
            }
            if (state.recordReadState != OperationState.Success) {
                put("records", "기록 불러오기를 완료한 후 저장해 주세요.")
            }
            if (draft.photoReference.isNullOrBlank()) put("photo", "사진을 선택해 주세요.")
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
            UUID.randomUUID().toString(), state.currentMeal,
            draft.copy(items = draft.items.toList())
        )
        pendingSave = pending
        save(pending)
    }

    private fun save(pending: PendingSave) {
        if (uiState.value.saveState == OperationState.Loading) return
        updateState { it.copy(saveState = OperationState.Loading, savingMealType = pending.meal) }
        viewModelScope.launch {
            // 파일 교체가 완료된 뒤 취소가 도착해 성공한 사진을 지우는 일을 방지합니다.
            withContext(NonCancellable) {
                var createdPhoto: String? = null
                var recordSaved = false
                try {
                    createdPhoto = photoStorage.copyPhoto(checkNotNull(pending.draft.photoReference))
                    val record = MealRecord(
                        recordId = pending.recordId,
                        date = pending.draft.date, time = pending.draft.time, mealType = pending.meal,
                        photoReference = createdPhoto,
                        items = pending.draft.items.map {
                            MealItem(itemId = it.itemId, food = checkNotNull(it.food),
                                amount = it.amountInput.trim().toDouble())
                        }
                    )
                    mealRepository.saveMeal(record)
                    recordSaved = true
                    updateState { state ->
                        state.copy(
                            draftsByMealType = state.draftsByMealType + (pending.meal to
                                pending.draft.copy(photoReference = null, items = emptyList())),
                            saveState = OperationState.Success, savingMealType = null
                        )
                    }
                    pendingSave = null
                } catch (error: Exception) {
                    createdPhoto?.takeUnless { recordSaved }?.let { reference ->
                        try { photoStorage.deletePhoto(reference) }
                        catch (cleanupError: Exception) { error.addSuppressed(cleanupError) }
                    }
                    updateState { it.copy(saveState = OperationState.Error(error), savingMealType = null) }
                }
            }
        }
    }
}
