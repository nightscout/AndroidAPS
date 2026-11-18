package app.aaps.plugins.source.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.source.SourceStrings

/**
 * The ONE+ / G7 status screen body.
 *
 * It is the shared pump overview: a banner with the phase, the facts of the sensor that feeds the
 * loop, and the buttons to start a sensor, follow the warm-up and read the driver log. Below the
 * facts come the cards the status screen always had: calibration, end of life, and the pre-soak
 * sensor with its Promote button.
 *
 * No frame of its own: the plugin tab gives it the toolbar, and the status Activity wraps it in
 * `CgmScaffold`.
 *
 * The idea follows the status screen of the G7 plugin tab in nightscout/AndroidAPS pull request
 * 5397 (Dexcom G7 direct, by t1dude, commit `bec1b16ba20`), written again on this driver. See the
 * NOTICE file of `:plugins:dexcom_oneplus`.
 */
@Composable
fun DexcomOnePlusStatusOverview(
    viewModel: DexcomOnePlusStatusViewModel,
    actions: DexcomOnePlusStatusActions,
    modifier: Modifier = Modifier,
) {
    val input by viewModel.input.collectAsState()
    val promoteResult by viewModel.promoteResult.collectAsState()
    val current = input ?: return
    val view = remember(current, actions) { DexcomOnePlusStatusState.build(current, viewModel.format, actions) }
    var promoteAsked by remember { mutableStateOf<Boolean?>(null) }

    PumpOverviewScreen(
        state = view.overview,
        modifier = modifier,
        customContent = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                view.calibration?.let { SensorCalibrationCard(it) }
                if (view.endOfLifePrompt) EndOfLifeCard(onOpenStart = actions.onOpenPresoak)
                if (view.stagingOffered) {
                    StagingCard(
                        card = view.staging,
                        formatGlucose = viewModel.format.glucose,
                        formatTime = viewModel.format.time,
                        onPromoteClick = { early -> promoteAsked = early },
                        onCancelStaging = { viewModel.cancelStaging() },
                        onOpenStart = actions.onOpenPresoak,
                    )
                }
                // Direct-to-watch: what the watch reports, only while the feature is on (the switch
                // itself is in the settings) and the Wear plugin is on.
                val watchHandover = viewModel.watchHandover
                val watchDirectOn by viewModel.watchDirectEnabled.collectAsState()
                if (watchDirectOn && watchHandover.isOffered) {
                    val watchReport by watchHandover.report.collectAsState()
                    DexcomOnePlusWatchDirectCard(
                        // Read again on every status tick, so a new pairing shows at once.
                        available = remember(current) { watchHandover.isAvailable() },
                        report = watchReport,
                    )
                }
                promoteResult?.let { message ->
                    Text(
                        text = stringResource(message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )

    promoteAsked?.let { early ->
        PromoteConfirmDialog(
            early = early,
            onConfirm = {
                promoteAsked = null
                viewModel.promote(early)
            },
            onDismiss = { promoteAsked = null },
        )
    }
}

@Composable
private fun PromoteConfirmDialog(early: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (early) SourceStrings.dexcom_oneplus_staging_promote_early_confirm_title
                    else SourceStrings.dexcom_oneplus_staging_promote_confirm_title,
                ),
            )
        },
        text = {
            Text(
                stringResource(
                    if (early) SourceStrings.dexcom_oneplus_staging_promote_early_confirm_message
                    else SourceStrings.dexcom_oneplus_staging_promote_confirm_message,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(SourceStrings.dexcom_oneplus_staging_promote_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(SourceStrings.dexcom_oneplus_staging_promote_dismiss)) }
        },
    )
}

/** The sensor in use is near its end and no replacement is warming up yet. */
@Composable
private fun EndOfLifeCard(onOpenStart: () -> Unit) {
    CgmCard(tone = CgmCardTone.Warning) {
        CgmCardHeader(stringResource(SourceStrings.dexcom_oneplus_end_of_life_title))
        Text(
            text = stringResource(SourceStrings.dexcom_oneplus_end_of_life_text),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onOpenStart) {
            Text(stringResource(SourceStrings.dexcom_oneplus_start_presoak))
        }
    }
}

/** What became of a finger prick value handed to the sensor itself. */
@Composable
private fun SensorCalibrationCard(card: DexcomOnePlusCalibrationCard) {
    CgmCard(tone = if (card.warning) CgmCardTone.Warning else CgmCardTone.Neutral) {
        CgmCardHeader(stringResource(SourceStrings.dexcom_oneplus_calibration_heading))
        card.request?.let { request ->
            Text(text = stringResource(request), style = MaterialTheme.typography.bodyMedium)
        }
        Text(text = stringResource(card.outcome), style = MaterialTheme.typography.bodyMedium)
        card.notSentReason?.let { reason ->
            Text(
                text = stringResource(reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        card.detail?.let { detail ->
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_calibration_detail, detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The collect-only second sensor.
 *
 * Promotion stays the only full width button in the card it belongs to, so it does not compete with
 * the buttons of the screen at the same visual weight.
 */
@Composable
private fun StagingCard(
    card: DexcomOnePlusStagingCard,
    formatGlucose: (Double) -> String,
    formatTime: (Long) -> String,
    onPromoteClick: (Boolean) -> Unit,
    onCancelStaging: () -> Unit,
    onOpenStart: () -> Unit,
) {
    val stateLabel = stringResource(
        when (card.state) {
            StagingState.ABSENT   -> SourceStrings.dexcom_oneplus_staging_state_absent
            StagingState.WARMUP   -> SourceStrings.dexcom_oneplus_staging_state_warmup
            StagingState.SETTLING -> SourceStrings.dexcom_oneplus_staging_state_settling
            StagingState.READY    -> SourceStrings.dexcom_oneplus_staging_state_ready
        },
    )
    CgmCard {
        CgmCardHeader(stringResource(SourceStrings.dexcom_oneplus_staging_heading_short)) {
            CgmStateChip(state = card.state.toUiState(), label = stateLabel)
        }
        if (card.state == StagingState.ABSENT) {
            // An empty state that explains what the slot is for and offers the way in, instead of
            // just reporting "None".
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_staging_none_short),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenStart) {
                Text(stringResource(SourceStrings.dexcom_oneplus_start_presoak))
            }
        } else {
            StagingDetails(card, formatGlucose, formatTime, onPromoteClick, onCancelStaging)
        }
    }
}

/** The pre-soak card while a sensor is in the slot: progress, evidence, promote and cancel. */
@Composable
private fun ColumnScope.StagingDetails(
    card: DexcomOnePlusStagingCard,
    formatGlucose: (Double) -> String,
    formatTime: (Long) -> String,
    onPromoteClick: (Boolean) -> Unit,
    onCancelStaging: () -> Unit,
) {
    CgmStepTimeline(
        labels = listOf(
            stringResource(SourceStrings.dexcom_oneplus_staging_state_warmup),
            stringResource(SourceStrings.dexcom_oneplus_staging_state_settling),
            stringResource(SourceStrings.dexcom_oneplus_staging_state_ready),
        ),
        reached = when (card.state) {
            StagingState.ABSENT   -> -1
            StagingState.WARMUP   -> 0
            StagingState.SETTLING -> 1
            StagingState.READY    -> 2
        },
    )
    // Evidence the staging sensor is really alive: without it the user only sees a state label
    // and cannot tell "settling with data" from "settling with a dead radio".
    CgmKeyValueRow(
        label = stringResource(SourceStrings.dexcom_oneplus_staging_readings_label),
        value = card.validCount.toString(),
    )
    val lastValue = card.lastValueMgdl
    val lastAt = card.lastValueAtMs
    if (lastValue != null && lastAt != null) {
        CgmKeyValueRow(
            label = stringResource(SourceStrings.dexcom_oneplus_staging_last_label),
            value = stringResource(SourceStrings.dexcom_oneplus_reading_at, formatGlucose(lastValue), formatTime(lastAt)),
        )
    } else {
        Text(
            text = stringResource(SourceStrings.dexcom_oneplus_staging_no_reading_yet),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (card.needsReconnect) {
        Text(
            text = stringResource(SourceStrings.dexcom_oneplus_staging_needs_reconnect),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    when (card.promote) {
        DexcomOnePlusPromoteOffer.NORMAL -> Button(
            onClick = { onPromoteClick(false) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(SourceStrings.dexcom_oneplus_staging_promote))
        }

        // Deliberately an outlined button with its own warning dialog: it gives up sensor
        // quality, so it must never look like the normal path.
        DexcomOnePlusPromoteOffer.EARLY  -> Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            OutlinedButton(
                onClick = { onPromoteClick(true) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(SourceStrings.dexcom_oneplus_staging_promote_early))
            }
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_staging_promote_early_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DexcomOnePlusPromoteOffer.NONE   -> Unit
    }
    TextButton(onClick = onCancelStaging, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(SourceStrings.dexcom_oneplus_staging_cancel))
    }
}
