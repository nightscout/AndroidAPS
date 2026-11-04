package app.aaps.ui.compose.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.alerts.EatReminderState
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.objects.extensions.tickerFlow
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.stringResource

/**
 * Bar for the running "time to eat" timer (issues #5327, #5372): how long until it is time to eat, the whole
 * wait, and Stop / +5 min. After it rang, Stop becomes Dismiss.
 *
 * On a client it shows the master's timer, and the buttons ask the master; [controlsEnabled] is false while
 * the master cannot be reached.
 *
 * @see EatReminderBannerRunningPreview
 * @see EatReminderBannerRungPreview
 */
@Composable
fun EatReminderBanner(
    state: EatReminderState?,
    onStop: () -> Unit,
    onExtend: () -> Unit,
    modifier: Modifier = Modifier,
    controlsEnabled: Boolean = true
) {
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        state?.let { EatReminderBannerContent(it, onStop, onExtend, modifier, controlsEnabled) }
    }
}

@Composable
internal fun EatReminderBannerContent(
    state: EatReminderState,
    onStop: () -> Unit,
    onExtend: () -> Unit,
    modifier: Modifier = Modifier,
    controlsEnabled: Boolean = true
) {
    val dateUtil = LocalDateUtil.current
    // Every second: the countdown shows seconds, and "how long do we still have to wait" is the reason for it.
    var now by remember { mutableLongStateOf(dateUtil.now()) }
    LaunchedEffect(Unit) {
        tickerFlow(1_000L).collect { now = dateUtil.now() }
    }

    val rang = state.hasRung(now)
    val totalMs = state.endsAt - state.startedAt
    // Rounded up, so it shows 0:01 until the end and never 0:00 before it rang.
    val leftSeconds = (state.remainingMs(now) + 999L) / 1000L
    val totalMinutes = T.msecs(state.durationMs).mins().toInt()

    OverviewBannerCard(
        icon = IcCarbs,
        color = AapsTheme.elementColors.carbs,
        title = stringResource(CoreUiStrings.eat_reminder_title),
        lines = listOf(
            if (rang) stringResource(CoreUiStrings.eat_reminder_rung)
            else stringResource(CoreUiStrings.eat_reminder_total_wait, stringResource(CoreUiStrings.format_mins, totalMinutes))
        ),
        progress = if (!rang && totalMs > 0) 1f - state.remainingMs(now).toFloat() / totalMs.toFloat() else null,
        modifier = modifier,
        trailing = {
            if (!rang)
                Text(
                    text = stringResource(CoreUiStrings.eat_reminder_countdown, (leftSeconds / 60).toInt(), (leftSeconds % 60).toInt()),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AapsTheme.elementColors.carbs
                )
        },
        // The master refuses both for a read-only client, so they are not offered there.
        actions = if (LocalClientRole.current.allows(ClientRole.MealAssistant)) {
            {
                OutlinedButton(onClick = onExtend, enabled = controlsEnabled) {
                    Text(stringResource(CoreUiStrings.eat_reminder_extend, EatReminder.EXTEND_MINUTES))
                }
                FilledTonalButton(onClick = onStop, enabled = controlsEnabled) {
                    Text(stringResource(if (rang) CoreUiStrings.dismiss else CoreUiStrings.stop))
                }
            }
        } else null
    )
}
