package app.aaps.ui.compose.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.ui.compose.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.objects.extensions.tickerFlow
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.ui.UiStrings
import kotlin.time.Clock
import app.aaps.core.keys.LongNonKey
import app.aaps.core.ui.compose.LocalPreferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 15 minutes, matching the official Eversense app's post-calibration countdown bar. */
private const val CALIBRATION_COUNTDOWN_MS = 15 * 60 * 1000L

/**
 * Home-screen banner like the official Eversense app's countdown bar after a calibration. Renders nothing once
 * [submittedAtMs] is more than 15 minutes old, or is 0 (never sent). Callers need no separate "is Eversense in use"
 * check: `EversenseLastCalibrationSubmittedAt` is written only when the Eversense plugin sent a calibration to the
 * transmitter (from the AAPS calibration dialog, an SMS or a client, all through the master), so a fresh value already
 * means Eversense is the source.
 */
@Composable
fun EversenseCalibrationBanner(
    submittedAtMs: Long,
    modifier: Modifier = Modifier
) {
    var now by remember(submittedAtMs) { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(submittedAtMs) {
        tickerFlow(1_000L).collect { now = Clock.System.now().toEpochMilliseconds() }
    }

    val remainingMs = submittedAtMs + CALIBRATION_COUNTDOWN_MS - now
    val visible = submittedAtMs > 0 && remainingMs > 0

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val totalSeconds = (remainingMs / 1000).coerceAtLeast(0).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val countdownText = "$minutes:${seconds.toString().padStart(2, '0')}"

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(UiStrings.eversense_calibration_in_progress, countdownText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}


@Composable
fun EversenseCalibrationProgress() {
    val preferences = LocalPreferences.current
    val submittedAt by preferences.observe(LongNonKey.EversenseLastCalibrationSubmittedAt).collectAsStateWithLifecycle()
    EversenseCalibrationBanner(submittedAtMs = submittedAt)
}
