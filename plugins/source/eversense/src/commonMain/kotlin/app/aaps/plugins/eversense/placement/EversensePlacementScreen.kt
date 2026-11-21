package app.aaps.plugins.eversense.placement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.eversense.EversenseStrings
import app.aaps.plugins.eversense.enums.SignalStrength
import app.aaps.plugins.eversense.ui.bars
import app.aaps.plugins.eversense.ui.color
import app.aaps.plugins.eversense.ui.instruction
import app.aaps.plugins.eversense.ui.label

/** The placement guide inside the Eversense screen, as the pairing wizard is: it has the toolbar while it is open. */
@Composable
internal fun EversensePlacementScreen(
    viewModel: EversensePlacementViewModel,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onClose: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = stringResource(EversenseStrings.eversense_placement_title)

    DisposableEffect(title) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiStrings.back))
                    }
                },
                actions = {}
            )
        )
        onDispose { setToolbarConfig(ToolbarConfig(title = "", navigationIcon = {}, actions = {})) }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onClose
    )
    // Diagnostic mode only while the guide is on screen: not in the background, not after it closes
    LifecycleResumeEffect(viewModel) {
        viewModel.resume()
        onPauseOrDispose { viewModel.pause() }
    }

    EversensePlacementContent(state = state, timeString = viewModel::timeString)
}

/** The guide without its view model, so that a test can show it. */
@Composable
internal fun EversensePlacementContent(state: EversensePlacementViewModel.UiState, timeString: (Long) -> String) {
    val signal = state as? EversensePlacementViewModel.UiState.Signal
    val strength = signal?.strength ?: SignalStrength.NO_SIGNAL
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        AapsCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
                Text(stringResource(EversenseStrings.eversense_placement_guide_heading), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(EversenseStrings.eversense_placement_guide_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SignalBars(strength = strength, lit = if (signal != null) strength.bars else 0)
                when (state) {
                    EversensePlacementViewModel.UiState.NotConnected -> {
                        Text(stringResource(EversenseStrings.eversense_not_connected), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(EversenseStrings.eversense_placement_instruction_not_connected))
                    }

                    EversensePlacementViewModel.UiState.Reading      ->
                        Text(stringResource(EversenseStrings.eversense_placement_reading), style = MaterialTheme.typography.headlineSmall)

                    is EversensePlacementViewModel.UiState.Signal    -> {
                        Text(stringResource(state.strength.label), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(CoreUiStrings.units_format_percent, state.percent))
                        Text(
                            stringResource(EversenseStrings.eversense_placement_last_update, timeString(state.readAt)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(stringResource(state.strength.instruction))
                    }
                }
            }
        }
    }
}

/** Five bars, [lit] of them in the colour of the signal; read out as "n of 5 signal bars". */
@Composable
private fun SignalBars(strength: SignalStrength, lit: Int) {
    val description = stringResource(EversenseStrings.eversense_placement_bars, lit)
    val litColor = strength.color()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(5) { index ->
            Box(
                Modifier
                    .width(AapsSpacing.xxLarge)
                    .height(AapsSpacing.xxLarge * (index + 1))
                    .background(if (index < lit) litColor else MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
            )
        }
    }
}
