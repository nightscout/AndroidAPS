package app.aaps.ui.compose.scenes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.objects.extensions.tickerFlow
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.overview.OverviewBannerCard

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
    chainTargetName: String? = null,
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
                chainTargetName = chainTargetName,
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
    /** Name of the scene that starts when this one ends; null when there is none (or it was deleted). */
    chainTargetName: String? = null,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    // Ticker for countdown — 30s matches GraphViewModel.ticker30s; text is minute-grain
    // and the progress bar stays visually smooth even on short scenes. tickerFlow's first
    // emission is immediate, so the initial `now` reflects subscription time without the
    // `remember { dateUtil.now() }` seed.
    var now by remember { mutableLongStateOf(dateUtil.now()) }
    LaunchedEffect(Unit) {
        tickerFlow(30_000L).collect { now = dateUtil.now() }
    }

    val remainingMs = state.remainingMs(now)
    val totalMs = state.durationMs

    val status = when {
        expired                                              -> stringResource(CoreUiStrings.scene_ended)
        remainingMs != null && remainingMs > 0 && totalMs > 0 -> formatDuration(remainingMs)
        else                                                 -> stringResource(CoreUiStrings.scene_active)
    }
    // What the scene sets in motion when it ends, as the scene list shows it
    val chain = if (!expired && chainTargetName != null) stringResource(CoreUiStrings.scene_chain_indicator, chainTargetName) else null
    OverviewBannerCard(
        icon = SceneIcons.fromKey(state.scene.icon).icon,
        color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else AapsTheme.elementColors.scene,
        title = state.scene.name,
        lines = listOfNotNull(status, chain),
        // Progress bar (only if duration-based and not expired)
        progress = if (!expired && remainingMs != null && totalMs > 0) 1f - (remainingMs.toFloat() / totalMs.toFloat()) else null,
        modifier = modifier,
        trailing = {
            if (expired) {
                FilledTonalButton(onClick = onDismiss) {
                    Text(stringResource(CoreUiStrings.close))
                }
            } else if (LocalClientRole.current.allows(ElementType.SCENE.role)) {
                // Active scene's End button — disabled on AAPSCLIENT while WS is down,
                // since the scene_stop envelope can't reach the master. Master is the
                // authoritative actor; locally faking "ended" would desync the chip.
                // Not shown at all for a role the master would refuse it for.
                FilledTonalButton(onClick = onEndClick, enabled = endEnabled) {
                    Text(stringResource(CoreUiStrings.scene_deactivate))
                }
            }
        }
    )
}
