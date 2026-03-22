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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.Scene
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsTheme
import kotlinx.coroutines.delay

/**
 * Banner showing the currently active scene with name, time remaining, progress, and End button.
 */
@Composable
fun ActiveSceneBanner(
    activeState: ActiveSceneState?,
    expired: Boolean = false,
    onEndClick: () -> Unit,
    onDismiss: () -> Unit = {},
    formatDuration: (Long) -> String,
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
    modifier: Modifier = Modifier
) {
    // Ticker for countdown
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }

    val remainingMs = state.remainingMs(now)
    val totalMs = state.durationMs

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = SceneIcons.fromKey(state.scene.icon).icon,
                    contentDescription = null,
                    tint = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else AapsTheme.elementColors.scene,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.scene.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else AapsTheme.elementColors.scene
                    )
                    if (expired) {
                        Text(
                            text = stringResource(R.string.scene_ended),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (remainingMs != null && remainingMs > 0 && totalMs > 0) {
                        Text(
                            text = stringResource(R.string.scene_time_remaining, formatDuration(remainingMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.scene_active),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (expired) {
                    FilledTonalButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                } else {
                    FilledTonalButton(onClick = onEndClick) {
                        Text(stringResource(R.string.scene_deactivate))
                    }
                }
            }
            // Progress bar (only if duration-based and not expired)
            if (!expired && remainingMs != null && totalMs > 0) {
                LinearProgressIndicator(
                    progress = { 1f - (remainingMs.toFloat() / totalMs.toFloat()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

// --- Previews ---

private fun sampleScene(name: String = "Exercise") = Scene(
    id = "preview",
    name = name,
    defaultDurationMinutes = 60
)

@Preview(showBackground = true)
@Composable
private fun ActiveSceneBannerTimedPreview() {
    val now = System.currentTimeMillis()
    MaterialTheme {
        Surface {
            ActiveSceneBannerContent(
                state = ActiveSceneState(
                    scene = sampleScene(),
                    activatedAt = now - 30 * 60_000L, // started 30 min ago
                    durationMs = 60 * 60_000L,        // 60 min total
                    priorState = ActiveSceneState.PriorState()
                ),
                onEndClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActiveSceneBannerExpiredPreview() {
    val now = System.currentTimeMillis()
    MaterialTheme {
        Surface {
            ActiveSceneBannerContent(
                state = ActiveSceneState(
                    scene = sampleScene(),
                    activatedAt = now - 60 * 60_000L,
                    durationMs = 60 * 60_000L,
                    priorState = ActiveSceneState.PriorState()
                ),
                expired = true,
                onEndClick = {},
                onDismiss = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActiveSceneBannerIndefinitePreview() {
    val now = System.currentTimeMillis()
    MaterialTheme {
        Surface {
            ActiveSceneBannerContent(
                state = ActiveSceneState(
                    scene = sampleScene("Sick Day"),
                    activatedAt = now - 120 * 60_000L,
                    durationMs = 0, // indefinite
                    priorState = ActiveSceneState.PriorState()
                ),
                onEndClick = {}
            )
        }
    }
}
