package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.pump.carelevo.CarelevoStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Clock

private const val MINUTE_MS = 60_000L

/** Often enough that the minutes shown are never more than a quarter of a minute late. */
private const val TICK_MS = 15_000L

/** Whole minutes left until [deadline], rounded up, so a part of a minute still counts. 0 once it has passed. */
internal fun activationMinutesLeft(deadline: Long, now: Long): Int =
    if (now >= deadline) 0 else ((deadline - now + MINUTE_MS - 1) / MINUTE_MS).toInt()

/**
 * The time left of the 20 minutes the patch gives from pairing to the start of the basal. The CareLevo app only names
 * the limit in its manual; here the user sees it on every step after pairing.
 */
@Composable
internal fun CarelevoActivationTimeLimit(deadline: Long) {
    val now by remember { clockTicks() }.collectAsStateWithLifecycle(nowMs())
    CarelevoActivationTimeLimitText(minutesLeft = activationMinutesLeft(deadline, now))
}

private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * The time now, again every [TICK_MS]. The waiting runs on [Dispatchers.Default], not in the composition: a loop with
 * `delay` in the composition never lets a Compose test become idle.
 */
private fun clockTicks(): Flow<Long> = flow {
    while (true) {
        emit(nowMs())
        delay(TICK_MS)
    }
}.flowOn(Dispatchers.Default)

/** The text alone, so a test can show it without a running clock. */
@Composable
internal fun CarelevoActivationTimeLimitText(minutesLeft: Int) {
    val over = minutesLeft <= 0
    Text(
        text = if (over) stringResource(CarelevoStrings.carelevo_activation_time_over, stringResource(CarelevoStrings.alarm_feat_title_alert_incomplete_patch_setting))
        else stringResource(CarelevoStrings.carelevo_activation_time_left, stringResource(CoreUiStrings.format_mins, minutesLeft)),
        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)
    )
}
