package app.aaps.ui.compose.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.stringResource

@Composable
fun BolusProgressBanner(
    state: BolusProgressState?,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    pumpStatus: String = "",
    queueStatus: AnnotatedString? = null
) {
    val shown = state?.takeUnless { it.isSMB }
    AnimatedVisibility(
        visible = shown != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        shown?.let { BolusProgressBannerContent(it, onStop, onDismiss, modifier, pumpStatus, queueStatus) }
    }
}

@Composable
internal fun BolusProgressBannerContent(
    state: BolusProgressState,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    pumpStatus: String = "",
    queueStatus: AnnotatedString? = null
) {
    val statusText = stringResource(state.status)
    // Nothing delivered yet: connecting, or waiting in the queue. Not while stalled, where the bar is hidden.
    val waiting = !state.stalled && state.percent <= 0
    OverviewBannerCard(
        icon = IcBolus,
        color = AapsTheme.elementColors.insulin,
        title = stringResource(CoreUiStrings.goingtodeliver, state.insulin),
        lines = when {
            // Stalled (client only): the progress stream stopped, so a present-tense "Delivering ..." line
            // and a frozen bar would read as live delivery.
            state.stalled -> listOf(
                stringResource(CoreUiStrings.clientcontrol_bolus_progress_stalled_title),
                stringResource(CoreUiStrings.clientcontrol_bolus_progress_stalled_body)
            )

            else -> buildList {
                if (statusText.isNotEmpty()) add(statusText)
                if (waiting) {
                    if (pumpStatus.isNotEmpty()) add(pumpStatus)
                    queueStatus?.text?.takeIf { it.isNotEmpty() }?.let { add(it) }
                }
            }
        },
        // Null while stalled: no bar. Zero percent means nothing received yet: a bar that only shows activity.
        progress = if (state.stalled) null else state.percent.coerceIn(0, 100) / 100f,
        indeterminate = waiting,
        modifier = modifier,
        actions = when {
            // Dismiss only hides this local view and does NOT stop the pump, so it stays neutral, not error-red.
            state.stalled -> {
                {
                    FilledTonalButton(onClick = onDismiss) {
                        Text(stringResource(CoreUiStrings.dismiss))
                    }
                }
            }

            state.percent < 100 -> {
                {
                    FilledTonalButton(
                        onClick = onStop,
                        enabled = !state.stopPressed && state.stopDeliveryEnabled,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(
                            if (state.stopPressed) stringResource(CoreUiStrings.stop_pressed)
                            else stringResource(CoreUiStrings.stop)
                        )
                    }
                }
            }

            else -> null
        }
    )
}
