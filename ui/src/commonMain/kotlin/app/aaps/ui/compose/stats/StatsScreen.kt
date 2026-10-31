package app.aaps.ui.compose.stats

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.stats.viewmodels.StatsTab
import app.aaps.ui.compose.stats.viewmodels.StatsUiState
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel
import kotlinx.coroutines.launch

/**
 * Composable screen displaying statistics in four tabs: Insulin (TDD), Time in range (TIR and Dexcom TIR),
 * Glucose (AGP and the glucose pentagon) and Cycle (the TDD cycle pattern).
 *
 * Tabs, not one long list: the sections are read one at a time, and only the tab on screen is
 * calculated. Four, not one per section: Material says to avoid more than four fixed tabs, and the
 * sections in one tab are read together. The screen opens on the tab used last.
 *
 * @param viewModel ViewModel containing all statistics state and business logic
 * @param onNavigateBack Callback when back navigation is requested
 */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onStart()
        }
    }

    val pagerState = rememberPagerState(initialPage = state.selectedTab.ordinal) { StatsTab.entries.size }
    val scope = rememberCoroutineScope()
    // A swipe and a tap on a tab both end on a settled page
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.selectTab(StatsTab.entries[it]) }
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(CoreUiStrings.statistics)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiStrings.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                StatsTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(text = tab.label(), maxLines = 2, textAlign = TextAlign.Center) }
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (StatsTab.entries[page]) {
                    StatsTab.INSULIN       -> InsulinTab(state, viewModel)
                    StatsTab.TIME_IN_RANGE -> TimeInRangeTab(state)
                    StatsTab.GLUCOSE       -> GlucoseTab(state, viewModel)
                    StatsTab.CYCLE         -> CycleTab(state, viewModel)
                }
            }
        }
    }

    if (state.showRecalculateDialog) {
        OkCancelDialog(
            message = stringResource(UiStrings.do_you_want_recalculate_tdd_stats),
            onConfirm = { viewModel.confirmRecalculateTdd() },
            onDismiss = { viewModel.dismissRecalculateDialog() }
        )
    }
}

@Composable
private fun StatsTab.label(): String = when (this) {
    StatsTab.INSULIN       -> stringResource(CoreUiStrings.insulin_label)
    StatsTab.TIME_IN_RANGE -> stringResource(CoreUiStrings.tir)
    StatsTab.GLUCOSE       -> stringResource(CoreUiStrings.glucose)
    StatsTab.CYCLE         -> stringResource(UiStrings.stats_tab_cycle)
}

@Composable
private fun InsulinTab(state: StatsUiState, viewModel: StatsViewModel) {
    StatsPage {
        StatsSection(
            title = stringResource(CoreUiStrings.tdd),
            trailing = if (state.tddLoading) null else {
                {
                    FilledTonalButton(onClick = { viewModel.showRecalculateDialog() }) {
                        Text(text = stringResource(UiStrings.recalculate))
                    }
                }
            }
        ) {
            Crossfade(
                targetState = state.tddLoading,
                label = stringResource(CoreUiStrings.loading)
            ) { isLoading ->
                if (isLoading) {
                    LoadingSection(
                        title = stringResource(CoreUiStrings.tdd),
                        message = stringResource(UiStrings.calculation_in_progress)
                    )
                } else {
                    state.tddStatsData?.let { data ->
                        TddStatsCompose(
                            tddStatsData = data,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** A tab of its own: 168 days of TDD, so it is calculated only when this tab is opened. */
@Composable
private fun CycleTab(state: StatsUiState, viewModel: StatsViewModel) {
    StatsPage {
        StatsSection(title = stringResource(CoreUiStrings.tdd_cycle_pattern)) {
            // Progress indicator (visible while still loading)
            if (state.tddCycleLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
                ) {
                    LinearProgressIndicator(
                        progress = { state.tddCycleProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                    Text(
                        text = stringResource(
                            UiStrings.calculation_in_progress_percent,
                            (state.tddCycleProgress * 100).toInt()
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Graph (shown as soon as data is available, even while loading continues)
            val cycleData = state.tddCyclePatternData
            if (cycleData != null) {
                TddCyclePatternCompose(
                    data = cycleData,
                    offset = state.tddCycleOffset,
                    onOffsetChange = { viewModel.updateCycleOffset(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (!state.tddCycleLoading) {
                Text(
                    text = stringResource(UiStrings.not_enough_data_for_cycles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimeInRangeTab(state: StatsUiState) {
    StatsPage {
        StatsSection(title = stringResource(CoreUiStrings.tir)) {
            Crossfade(
                targetState = state.tirLoading,
                label = stringResource(CoreUiStrings.loading)
            ) { isLoading ->
                if (isLoading) {
                    LoadingSection(
                        title = stringResource(CoreUiStrings.tir),
                        message = stringResource(UiStrings.calculation_in_progress)
                    )
                } else {
                    state.tirStatsData?.let { data ->
                        TirStatsCompose(
                            tirStatsData = data,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        StatsSection(title = stringResource(UiStrings.dexcom_tir)) {
            Crossfade(
                targetState = state.dexcomTirLoading,
                label = stringResource(CoreUiStrings.loading)
            ) { isLoading ->
                if (isLoading) {
                    LoadingSection(
                        title = stringResource(UiStrings.dexcom_tir),
                        message = stringResource(UiStrings.calculation_in_progress)
                    )
                } else {
                    state.dexcomTirData?.let { data ->
                        DexcomTirStatsCompose(
                            dexcomTir = data,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlucoseTab(state: StatsUiState, viewModel: StatsViewModel) {
    StatsPage {
        StatsSection(title = stringResource(UiStrings.agp)) {
            AGPStats(
                selectedRange = state.agpRange,
                onSelectRange = viewModel::loadAgpStats,
                data = state.agpStatsData,
                isLoading = state.agpLoading,
                hasError = state.agpError
            )
        }
        // Comprehensive Glucose Pentagon, drawn from the Dexcom TIR numbers
        StatsSection(title = stringResource(UiStrings.cgp_title)) {
            Crossfade(targetState = state.dexcomTirLoading, label = "cgp_loading") { isLoading ->
                if (isLoading) {
                    LoadingSection(title = stringResource(UiStrings.cgp_title), message = stringResource(UiStrings.calculation_in_progress))
                } else {
                    state.dexcomTirData?.let { data ->
                        GlucosePentagonCompose(dexcomTir = data, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** One tab: its sections, scrolling on their own. */
@Composable
private fun StatsPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        content = content
    )
}

@Composable
private fun StatsSection(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.extraLarge)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    // A heading, so a screen reader can jump between the sections of this screen.
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                trailing?.invoke()
            }
            content()
        }
    }
}
