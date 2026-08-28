package app.aaps.ui.compose.scenes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.objects.extensions.tickerFlow
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * Banner showing the currently active scene with name, time remaining, progress, and End button.
 *
 * @see ActiveSceneBannerTimedPreview
 * @see ActiveSceneBannerExpiredPreview
 * @see ActiveSceneBannerIndefinitePreview
 */
@Composable
fun ActiveSceneBanner(
    activeState: ActiveSceneState?,
    expired: Boolean = false,
    onEndClick: () -> Unit,
    onDismiss: () -> Unit = {},
    formatDuration: (Long) -> String,
    endEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = activeState != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        activeState?.let { state ->
            ActiveSceneBannerContent(
                state = state,
                expired = expired,
                onEndClick = onEndClick,
                onDismiss = onDismiss,
                formatDuration = formatDuration,
                endEnabled = endEnabled,
                modifier = modifier
            )
        }
    }
}

@Composable
internal fun ActiveSceneBannerContent(
    state: ActiveSceneState,
    expired: Boolean = false,
    onEndClick: () -> Unit,
    onDismiss: () -> Unit = {},
    formatDuration: (Long) -> String = { ms -> "${(ms / 60000L).toInt()}m" },
    endEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Ticker for countdown — 30s matches GraphViewModel.ticker30s; text is minute-grain
    // and the progress bar stays visually smooth even on short scenes. tickerFlow's first
    // emission is immediate, so the initial `now` reflects subscription time without the
    // `remember { System.currentTimeMillis() }` seed.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        tickerFlow(30_000L).collect { now = System.currentTimeMillis() }
    }

    val remainingMs = state.remainingMs(now)
    val totalMs = state.durationMs

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
    ) {
        Column(modifier = Modifier.padding(AapsSpacing.large)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = SceneIcons.fromKey(state.scene.icon).icon,
                    contentDescription = null,
                    tint = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else AapsTheme.elementColors.scene,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.scene.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else AapsTheme.elementColors.scene
                    )
                    if (expired) {
                        Text(
                            text = stringResource(CoreUiStrings.scene_ended),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (remainingMs != null && remainingMs > 0 && totalMs > 0) {
                        Text(
                            text = formatDuration(remainingMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(CoreUiStrings.scene_active),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (expired) {
                    FilledTonalButton(onClick = onDismiss) {
                        Text(stringResource(CoreUiStrings.close))
                    }
                } else {
                    // Active scene's End button — disabled on AAPSCLIENT while WS is down,
                    // since the scene_stop envelope can't reach the master. Master is the
                    // authoritative actor; locally faking "ended" would desync the chip.
                    FilledTonalButton(onClick = onEndClick, enabled = endEnabled) {
                        Text(stringResource(CoreUiStrings.scene_deactivate))
                    }
                }
            }
            // Progress bar (only if duration-based and not expired)
            if (!expired && remainingMs != null && totalMs > 0) {
                LinearProgressIndicator(
                    progress = { 1f - (remainingMs.toFloat() / totalMs.toFloat()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AapsSpacing.medium)
                )
            }
        }
    }
}
