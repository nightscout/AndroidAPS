package app.aaps.ui.compose.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.ui.R
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel

/**
 * Composable screen displaying statistics including TDD, TIR, Dexcom TIR, and Activity Monitor.
 * Uses pure Material3 design with Cards and standard typography.
 *
 * @param viewModel ViewModel containing all statistics state and business logic
 * @param onNavigateBack Callback when back navigation is requested
 */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(app.aaps.core.ui.R.string.statistics)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.back)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // TDD Section
            AapsCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleTddExpanded() }
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(app.aaps.core.ui.R.string.tdd),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (state.tddExpanded && !state.tddLoading) {
                            FilledTonalButton(onClick = { viewModel.recalculateTdd(context) }) {
                                Text(text = stringResource(R.string.recalculate))
                            }
                        }
                        Icon(
                            imageVector = if (state.tddExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = state.tddExpanded) {
                        Crossfade(
                            targetState = state.tddLoading,
                            label = stringResource(app.aaps.core.ui.R.string.loading)
                        ) { isLoading ->
                            if (isLoading) {
                                LoadingSection(
                                    title = stringResource(app.aaps.core.ui.R.string.tdd),
                                    message = stringResource(R.string.calculation_in_progress)
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

            // TIR Section
            AapsCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleTirExpanded() }
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(app.aaps.core.ui.R.string.tir),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (state.tirExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = state.tirExpanded) {
                        Crossfade(
                            targetState = state.tirLoading,
                            label = stringResource(app.aaps.core.ui.R.string.loading)
                        ) { isLoading ->
                            if (isLoading) {
                                LoadingSection(
                                    title = stringResource(app.aaps.core.ui.R.string.tir),
                                    message = stringResource(R.string.calculation_in_progress)
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
                }
            }

            // Dexcom TIR Section
            AapsCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleDexcomTirExpanded() }
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dexcom_tir),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (state.dexcomTirExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = state.dexcomTirExpanded) {
                        Crossfade(
                            targetState = state.dexcomTirLoading,
                            label = stringResource(app.aaps.core.ui.R.string.loading)
                        ) { isLoading ->
                            if (isLoading) {
                                LoadingSection(
                                    title = stringResource(R.string.dexcom_tir),
                                    message = stringResource(R.string.calculation_in_progress)
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

            // Activity Section
            AapsCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleActivityExpanded() }
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.activity_monitor),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (state.activityExpanded && !state.activityLoading) {
                            FilledTonalButton(onClick = { viewModel.resetActivityStats(context) }) {
                                Text(text = stringResource(app.aaps.core.ui.R.string.reset))
                            }
                        }
                        Icon(
                            imageVector = if (state.activityExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = state.activityExpanded) {
                        Crossfade(
                            targetState = state.activityLoading,
                            label = stringResource(app.aaps.core.ui.R.string.loading)
                        ) { isLoading ->
                            if (isLoading) {
                                LoadingSection(
                                    title = stringResource(R.string.activity_monitor),
                                    message = stringResource(R.string.calculation_in_progress)
                                )
                            } else {
                                state.activityStatsData?.let { data ->
                                    ActivityStatsCompose(
                                        activityStats = data,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // TDD Cycle Pattern Section
            AapsCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleTddCycleExpanded() }
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(app.aaps.core.ui.R.string.tdd_cycle_pattern),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (state.tddCycleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = state.tddCycleExpanded) {
                        when {
                            state.tddCycleLoading -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { state.tddCycleProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.calculation_in_progress_percent,
                                            (state.tddCycleProgress * 100).toInt()
                                        ),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            state.tddCyclePatternData != null -> TddCyclePatternCompose(
                                data = state.tddCyclePatternData!!,
                                offset = state.tddCycleOffset,
                                onOffsetChange = { viewModel.updateCycleOffset(it) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            else -> Text(
                                text = stringResource(R.string.not_enough_data_for_cycles),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // CGP Section (Comprehensive Glucose Pentagon) - always expanded
            AapsCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.cgp_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Crossfade(targetState = state.dexcomTirLoading, label = "cgp_loading") { isLoading ->
                        if (isLoading) {
                            LoadingSection(title = stringResource(R.string.cgp_title), message = stringResource(R.string.calculation_in_progress))
                        } else {
                            state.dexcomTirData?.let { data ->
                                GlucosePentagonCompose(dexcomTir = data, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}
