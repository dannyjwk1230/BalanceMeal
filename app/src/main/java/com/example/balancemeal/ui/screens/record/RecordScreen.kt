package com.example.balancemeal.ui.screens.record

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.balancemeal.model.MealKey
import com.example.balancemeal.model.MealType
import com.example.balancemeal.ui.meal.*
import com.example.balancemeal.ui.screens.food.FoodSelectionPanel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// 두 패널의 최소 사용 너비: 편집 영역 320dp + 선택 영역 280dp + 여백
private val MIN_EDIT_PANE_WIDTH = 320.dp
private val MIN_FOOD_PANE_WIDTH = 280.dp
private val PANE_GAP = 16.dp

/**
 * 기기 종류나 방향이 아니라 실제 창 너비와 두 패널의 최소 사용 너비를 기준으로
 * 단일 화면(좁은) / 2-pane(넓은) 레이아웃을 선택합니다.
 */
@Composable
fun RecordScreen(
    state: MealUiState,
    onEvent: (MealEvent) -> Unit,
    /** 좁은 화면 전용: 음식 선택 화면으로 이동하는 콜백 */
    onFoodSelection: (String) -> Unit
) {
    // 음식 선택 패널 요청 — ViewModel이 아닌 rememberSaveable로 보관해 레이아웃 전환에서도 유지
    var foodPickerRequest by rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.listSaver(
            save = { req ->
                if (req == null) listOf<String>()
                else listOf(req.itemId, req.mealType.name, req.date.toString())
            },
            restore = { list ->
                if (list.size == 3)
                    FoodPickerRequest(list[0], MealType.valueOf(list[1]), LocalDate.parse(list[2]))
                else null
            }
        )
    ) { mutableStateOf<FoodPickerRequest?>(null) }

    // 음식 검색어 — 패널 열림 대상이 바뀔 때만 초기화
    var foodQuery by rememberSaveable(
        foodPickerRequest?.itemId,
        foodPickerRequest?.mealType,
        foodPickerRequest?.date
    ) { mutableStateOf("") }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val isTwoPane = availableWidth >= MIN_EDIT_PANE_WIDTH + PANE_GAP + MIN_FOOD_PANE_WIDTH

        if (isTwoPane) {
            // === 넓은 화면: Row로 두 패널을 나란히 배치 ===
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(PANE_GAP)
            ) {
                // 왼쪽: 식사 기록 편집 영역 (독립 스크롤)
                val editWeight = (MIN_EDIT_PANE_WIDTH.value /
                    (MIN_EDIT_PANE_WIDTH.value + MIN_FOOD_PANE_WIDTH.value)).coerceIn(0.4f, 0.6f)
                Box(Modifier.weight(editWeight)) {
                    RecordEditPane(
                        state = state,
                        onEvent = onEvent,
                        isTwoPane = true,
                        activeFoodPickerRequest = foodPickerRequest,
                        onOpenFoodPicker = { request ->
                            foodPickerRequest = request
                        },
                        onFoodSelectionNavigate = onFoodSelection // 사용 안 함 (넓은 화면)
                    )
                }
                // 오른쪽: 음식 검색·선택 영역 (독립 스크롤)
                Box(Modifier.weight(1f - editWeight)) {
                    if (foodPickerRequest != null) {
                        val req = foodPickerRequest!!
                        val targetAvailable = state.draftsByKey[MealKey(req.date, req.mealType)]
                            ?.items?.any { it.itemId == req.itemId } == true
                        FoodSelectionPanel(
                            state = state,
                            query = foodQuery,
                            onQueryChange = { foodQuery = it },
                            targetAvailable = targetAvailable,
                            onSelectFood = { code ->
                                onEvent(
                                    MealEvent.SelectFood(
                                        req.itemId, code, req.mealType, req.date
                                    )
                                )
                                foodPickerRequest = null
                            },
                            onCancel = { foodPickerRequest = null },
                            onEvent = onEvent
                        )
                    } else {
                        // 패널 비활성: 안내 메시지
                        Box(
                            Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(
                                "음식 선택 또는 변경 버튼을 누르면 여기에서 검색할 수 있습니다.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            // === 좁은 화면: 단일 패널, 음식 선택은 Navigation으로 ===
            RecordEditPane(
                state = state,
                onEvent = onEvent,
                isTwoPane = false,
                activeFoodPickerRequest = null,
                onOpenFoodPicker = null,
                onFoodSelectionNavigate = onFoodSelection
            )
        }
    }
}

/**
 * 식사 기록 편집 영역입니다. 단일/2-pane 양쪽에서 재사용합니다.
 *
 * [isTwoPane] = true 이면 [onOpenFoodPicker]로 오른쪽 패널을 열고,
 * false이면 [onFoodSelectionNavigate]로 Navigation을 사용합니다.
 * [activeFoodPickerRequest]는 현재 패널에서 선택 중인 항목을 강조 표시하는 데 사용합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordEditPane(
    state: MealUiState,
    onEvent: (MealEvent) -> Unit,
    isTwoPane: Boolean,
    activeFoodPickerRequest: FoodPickerRequest?,
    onOpenFoodPicker: ((FoodPickerRequest) -> Unit)?,
    onFoodSelectionNavigate: (String) -> Unit
) {
    val draft = state.draftsByMealType[state.currentMeal]
    val currentKey = MealKey(
        state.selectedDate ?: draft?.date ?: LocalDate.MIN,
        state.currentMeal
    )
    val editable = state.savingMealKey != currentKey
    var showTime by rememberSaveable(state.selectedDate, state.currentMeal) {
        mutableStateOf(false)
    }

    // 각 패널은 독립적으로 스크롤 — 스크롤 위치를 선택 날짜·끼니 기준으로 보존
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()           // 키보드에 가려지지 않게 함
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = if (isTwoPane) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("기록", style = MaterialTheme.typography.headlineMedium)

        // 주간 날짜 선택기
        if (state.today != null && state.selectedDate != null && state.displayedWeekStart != null) {
            WeeklyDateSelector(
                state.displayedWeekStart,
                state.selectedDate,
                state.today,
                { onEvent(MealEvent.MoveWeek(it)) },
                { onEvent(MealEvent.SetDate(it)) }
            )
        }

        // 식사 시간 버튼
        if (draft != null) {
            OutlinedButton(
                onClick = { showTime = true },
                enabled = editable,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("식사 시간: ${draft.time.format(DateTimeFormatter.ofPattern("HH:mm"))}")
            }
        }
        state.timeValidationMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        // 끼니 선택 칩
        MealType.entries.chunked(2).forEach { meals ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meals.forEach { meal ->
                    FilterChip(
                        selected = state.currentMeal == meal,
                        onClick = { onEvent(MealEvent.SelectMeal(meal)) },
                        label = {
                            Text(if (meal == MealType.SNACK_DRINK) "간식·음료" else meal.displayName)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 사진 섹션
        MealPhotoSection(
            mealType = state.currentMeal,
            photoReferences = draft?.photoReferences.orEmpty(),
            targetDate = state.selectedDate?.toString(),
            enabled = editable,
            onPhotoRemoved = { reference ->
                draft?.let {
                    onEvent(MealEvent.RemovePhoto(reference, state.currentMeal, it.date))
                }
            },
            onPhotoSelected = { target, references, date ->
                date?.let {
                    onEvent(MealEvent.AddPhotos(references, target, LocalDate.parse(it)))
                }
            }
        )

        // 중복 기록 경고
        state.duplicateMealKeys.forEach { (key, ids) ->
            Text(
                "중복 기록: ${key.date} ${key.mealType.displayName} ${ids.size}건. 기존 기록을 보존했습니다.",
                color = MaterialTheme.colorScheme.error
            )
        }

        // 음식 목록
        Text("음식 목록", style = MaterialTheme.typography.titleMedium)
        if (draft?.items.isNullOrEmpty()) {
            Text("음식·반찬 추가 버튼으로 항목을 추가해 주세요.")
        }
        draft?.items?.forEach { item ->
            key(item.itemId) {
                // 넓은 화면: 현재 패널에서 선택 중인 항목 강조 표시
                val isBeingEdited = isTwoPane &&
                    activeFoodPickerRequest?.itemId == item.itemId &&
                    activeFoodPickerRequest.mealType == state.currentMeal
                MealItemRow(
                    item = item,
                    enabled = editable,
                    error = state.inputErrors[item.itemId],
                    nutrients = state.nutritionPreview.byItemId[item.itemId],
                    isHighlighted = isBeingEdited,
                    onSelectFood = {
                        if (isTwoPane && onOpenFoodPicker != null) {
                            // 넓은 화면: 오른쪽 패널 열기
                            onOpenFoodPicker(
                                FoodPickerRequest(
                                    itemId = item.itemId,
                                    mealType = state.currentMeal,
                                    date = state.selectedDate ?: draft.date
                                )
                            )
                        } else {
                            // 좁은 화면: Navigation으로 이동
                            onFoodSelectionNavigate(item.itemId)
                        }
                    },
                    onAmountChange = { onEvent(MealEvent.SetAmount(item.itemId, it)) },
                    onDelete = { onEvent(MealEvent.RemoveItem(item.itemId)) }
                )
            }
        }

        // 음식 추가 버튼
        Button(
            onClick = { onEvent(MealEvent.AddItem) },
            enabled = draft != null && editable,
            modifier = Modifier.fillMaxWidth()
        ) { Text("음식·반찬 추가") }

        // 영양 합계
        MealNutritionSummary(state.nutritionPreview.total)

        // 저장 버튼 — 시스템 영역·키보드에 가려지지 않게 imePadding + padding으로 처리
        val isDeletionMode = draft?.items.isNullOrEmpty() && state.editingRecordId != null
        Button(
            onClick = { onEvent(MealEvent.SaveCurrentMeal) },
            enabled = state.foodReadState == OperationState.Success &&
                state.recordReadState == OperationState.Success &&
                state.saveState != OperationState.Loading &&
                (isDeletionMode || state.timeValidationMessage == null),
            colors = if (isDeletionMode) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) else ButtonDefaults.buttonColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    state.saveState == OperationState.Loading -> if (isDeletionMode) "삭제 중" else "저장 중"
                    isDeletionMode -> "기록 삭제"
                    state.editingRecordId != null -> "변경사항 저장"
                    else -> "식사 저장"
                }
            )
        }
        if (state.saveState == OperationState.Success) Text(
            if (state.editingRecordId == null && draft?.items.isNullOrEmpty()) "식사 기록을 삭제했습니다."
            else "식사를 저장했습니다."
        )
        state.inputErrors["items"]?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        MealFeedback(state, onEvent, food = true, records = true, save = true)

        // 하단 여백 — 저장 버튼이 내비게이션 바에 가려지지 않게 함
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    // 시간 선택 다이얼로그
    if (showTime && draft != null && editable) {
        val picker = rememberTimePickerState(
            initialHour = draft.time.hour,
            initialMinute = draft.time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("식사 시간 선택") },
            text = { TimeInput(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    onEvent(MealEvent.SetTime(LocalTime.of(picker.hour, picker.minute)))
                    showTime = false
                }) { Text("시간 적용") }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text("취소") }
            }
        )
    }
}
