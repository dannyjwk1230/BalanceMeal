package com.example.balancemeal.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.balancemeal.ui.meal.MealDestinationScreen
import com.example.balancemeal.ui.meal.MealUiState
import com.example.balancemeal.ui.screens.history.MealDetailPlaceholder

private data class TopDestination(val key: NavKey, val label: String, val icon: String)
private val topDestinations = listOf(
    TopDestination(Home, "홈", "⌂"),
    TopDestination(Record, "기록", "＋"),
    TopDestination(History, "내역", "≡"),
    TopDestination(Analysis, "분석", "▥")
)

// Record 화면의 2-pane 임계치: RecordScreen 내부 기준과 동일하게 맞춥니다.
// (NavigationRail 너비 ~72dp 포함, 여기서는 창 전체 너비 기준으로 여유를 줍니다)
private val RECORD_TWO_PANE_MIN_WIDTH = 672.dp  // 320 + 16 + 280 + Rail 56

/** 그래프는 VM을 생성하지 않습니다. 기본 content는 IO 없는 프리뷰용입니다. */
@OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun BalanceMealApp(
    startDestination: NavKey = Home,
    onRecordEntered: () -> Unit = {},
    destinationContent: @Composable (NavKey, (NavKey) -> Unit, () -> Unit) -> Unit =
        { key, navigate, back -> MealDestinationScreen(key, MealUiState(), {}, navigate, back) }
) {
    val backStack = rememberNavBackStack(startDestination)
    fun selectTab(key: NavKey) {
        if (backStack.firstOrNull() == key) return
        if (key == Record) onRecordEntered()
        backStack.clear()
        backStack.add(key)
    }
    fun navigate(key: NavKey) {
        if (topDestinations.any { it.key == key }) selectTab(key)
        else if (backStack.lastOrNull() != key) backStack.add(key)
    }
    val goBack: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = maxWidth

        // 좁은 → 넓은 화면 전환: FoodSelection 목적지가 백스택에 있으면 팝합니다.
        // 넓은 화면에서는 패널로 처리되므로 Navigation 엔트리가 필요 없습니다.
        LaunchedEffect(Unit) {
            snapshotFlow { availableWidth }.collect { width ->
                if (width >= RECORD_TWO_PANE_MIN_WIDTH) {
                    // FoodSelection 엔트리가 있으면 Record까지 팝
                    while (backStack.lastOrNull() is FoodSelection) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                }
            }
        }

        val directive = androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective(
            androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2()
        ).copy(maxHorizontalPartitions = if (availableWidth >= 840.dp) 2 else 1)
        val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
        NavigationSuiteScaffold(
            layoutType = if (availableWidth < 600.dp) NavigationSuiteType.NavigationBar
                else NavigationSuiteType.NavigationRail,
            navigationSuiteItems = {
                topDestinations.forEach { destination ->
                    item(
                        selected = backStack.firstOrNull() == destination.key,
                        onClick = { selectTab(destination.key) },
                        icon = { Text(destination.icon) },
                        label = { Text(destination.label) }
                    )
                }
            }
        ) {
            NavDisplay(
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                backStack = backStack,
                onBack = goBack,
                sceneStrategies = listOf(listDetailStrategy),
                entryProvider = entryProvider {
                    entry<Home> { destinationContent(Home, ::navigate, goBack) }
                    entry<Record> { destinationContent(Record, ::navigate, goBack) }
                    entry<History>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { MealDetailPlaceholder() }
                        )
                    ) { destinationContent(History, ::navigate, goBack) }
                    entry<Analysis> { destinationContent(Analysis, ::navigate, goBack) }
                    entry<FoodSelection> { destinationContent(it, ::navigate, goBack) }
                    entry<MealDetail>(metadata = ListDetailSceneStrategy.detailPane()) {
                        destinationContent(it, ::navigate, goBack)
                    }
                    entry<DataSources> { destinationContent(DataSources, ::navigate, goBack) }
                }
            )
        }
    }
}
