package com.example.balancemeal.ui.meal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.balancemeal.AppContainer
import com.example.balancemeal.navigation.*
import com.example.balancemeal.ui.screens.home.HomeScreen
import com.example.balancemeal.ui.screens.record.RecordScreen
import com.example.balancemeal.ui.screens.history.HistoryScreen
import com.example.balancemeal.ui.screens.analysis.AnalysisScreen
import com.example.balancemeal.ui.screens.food.FoodSelectionScreen
import com.example.balancemeal.ui.screens.detail.MealDetailScreen
import com.example.balancemeal.ui.screens.sources.DataSourcesScreen

@Composable
fun MealAppRoute(container: AppContainer) {
    // NavDisplay 엔트리 밖의 Activity 소유자로 생성합니다. 모든 목적지에 같은 VM을 전달합니다.
    val sharedViewModel: MealViewModel = viewModel(factory = container.mealViewModelFactory)
    BalanceMealApp(onRecordEntered = { sharedViewModel.onEvent(MealEvent.EnterRecord) }) { key, navigate, onBack ->
        MealRoute(key, sharedViewModel, navigate, onBack)
    }
}

@Composable
fun MealRoute(
    destination: NavKey,
    viewModel: MealViewModel,
    navigate: (NavKey) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(destination) {
        if (destination == Record) {
            viewModel.onEvent(MealEvent.EnsureRecordInitialized)
            while (true) {
                viewModel.onEvent(MealEvent.RefreshTime)
                kotlinx.coroutines.delay(15_000)
            }
        }
    }
    MealDestinationScreen(destination, state, viewModel::onEvent, navigate, onBack)
}

/** Screen에는 Repository나 ViewModel을 전달하지 않습니다. 프리뷰도 이 경계를 사용합니다. */
@Composable
fun MealDestinationScreen(
    destination: NavKey,
    state: MealUiState,
    onEvent: (MealEvent) -> Unit,
    navigate: (NavKey) -> Unit,
    onBack: () -> Unit
) {
    when (destination) {
        Home -> HomeScreen(state, onEvent, { navigate(Record) }, { navigate(DataSources) })
        Record -> RecordScreen(state, onEvent, { itemId -> navigate(FoodSelection(state.currentMeal, itemId, state.selectedDate?.toString())) })
        History -> HistoryScreen(state, onEvent, { navigate(MealDetail(it)) })
        Analysis -> AnalysisScreen(state, onEvent)
        is FoodSelection -> {
            var query by rememberSaveable(destination.date, destination.mealType, destination.itemId) { mutableStateOf("") }
            val targetAvailable = state.draftsByKey[com.example.balancemeal.model.MealKey(
                destination.date?.let(java.time.LocalDate::parse) ?: state.selectedDate!!, destination.mealType)]?.items
                ?.any { it.itemId == destination.itemId } == true
            FoodSelectionScreen(
                state = state, query = query, onQueryChange = { query = it },
                targetAvailable = targetAvailable,
                onSelectFood = { code ->
                    onEvent(MealEvent.SelectFood(destination.itemId, code, destination.mealType, destination.date?.let(java.time.LocalDate::parse)))
                    onBack()
                },
                onEvent = onEvent, onBack = onBack
            )
        }
        is MealDetail -> MealDetailScreen(destination.recordId, state, onEvent)
        DataSources -> DataSourcesScreen(state, onEvent, onBack)
        else -> error("Unknown destination: $destination")
    }
}
