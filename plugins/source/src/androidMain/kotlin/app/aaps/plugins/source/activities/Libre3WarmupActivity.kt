package app.aaps.plugins.source.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.libre3.Libre3CgmDrivers
import app.aaps.plugins.libre3.Libre3LogMarkers
import app.aaps.plugins.libre3.Libre3WarmupState
import app.aaps.plugins.source.R
import app.aaps.plugins.source.compose.CgmCard
import app.aaps.plugins.source.compose.CgmCardHeader
import app.aaps.plugins.source.compose.CgmCardTone
import app.aaps.plugins.source.compose.CgmLazyColumn
import app.aaps.plugins.source.compose.CgmScaffold
import app.aaps.plugins.source.compose.CgmStateChip
import app.aaps.plugins.source.compose.CgmWarmupRing
import app.aaps.plugins.source.compose.Libre3UiLabels
import app.aaps.plugins.source.compose.Libre3WarmupCountdown
import app.aaps.plugins.source.compose.toCgmWarmupInfo
import app.aaps.plugins.source.compose.rememberCgmWindow
import app.aaps.plugins.source.compose.toUiState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Warm-up countdown for the Libre 3 session.
 *
 * In portrait the ring is the subject and the details sit under it; on a short screen (phone in
 * landscape) the ring moves beside them, which is the only arrangement that fits about 360 dp of
 * height. The screen scrolls either way, so nothing is drawn past the bottom edge.
 */
class Libre3WarmupActivity : MetroAppCompatActivity() {

    @Inject lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Other apps may not draw over this screen: an overlay could hide what a tap really does, here on a
        // sensor (start, calibration, pre-soak). Same as ComposeMainActivity. Needs HIDE_OVERLAY_WINDOWS.
        window.setHideOverlayWindows(true)
        setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    Libre3WarmupScreen(
                        onBack = { finish() },
                        onOpenLog = {
                            startActivity(CgmDriverLogActivity.intent(this, setOf(Libre3LogMarkers.TAG)))
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun Libre3WarmupScreen(
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = rememberCgmWindow()
    val driver = remember { Libre3CgmDrivers.default() }
    var state by remember { mutableStateOf(driver.warmupState()) }
    var remainingMs by remember { mutableStateOf<Long?>(null) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            state = driver.warmupState()
            val info = state.toCgmWarmupInfo()
            remainingMs = Libre3WarmupCountdown.remainingMs(info, now)
            finished = Libre3WarmupCountdown.isFinished(info, now)
            delay(1_000L)
        }
    }

    CgmScaffold(
        title = stringResource(R.string.libre3_warmup_title),
        onNavigate = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = onOpenLog) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.cgm_driver_log_open),
                )
            }
        },
        constrainWidth = !window.isShort,
    ) {
        if (window.isShort) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AapsSpacing.extraLarge),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.xxLarge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CgmWarmupRing(
                    // Libre 3 never reports a total, only what is left, so a fraction cannot be
                    // drawn honestly. The full ring says "running" without inventing progress.
                    progress = null,
                    state = state.phase.toUiState(),
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                ) {
                    RingCenter(state, remainingMs)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.large),
                ) {
                    PhaseChip(state)
                    WarmupDetails(state = state, finished = finished)
                }
            }
        } else {
            CgmLazyColumn {
                item(key = "ring") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large),
                    ) {
                        PhaseChip(state)
                        CgmWarmupRing(
                            progress = null,
                            state = state.phase.toUiState(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RingCenter(state, remainingMs)
                        }
                    }
                }
                item(key = "details") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large),
                    ) {
                        WarmupDetails(state = state, finished = finished)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseChip(state: Libre3WarmupState) {
    CgmStateChip(
        state = state.phase.toUiState(),
        label = Libre3UiLabels.phaseLabel(state.phase),
    )
}

@Composable
private fun RingCenter(state: Libre3WarmupState, remainingMs: Long?) {
    val countdown = remainingMs?.let { Libre3WarmupCountdown.format(it) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall),
    ) {
        Text(
            text = countdown ?: stringResource(R.string.libre3_warmup_countdown_unknown),
            style = MaterialTheme.typography.headlineMedium,
            color = if (state.phase == Libre3WarmupState.Phase.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
        )
        if (countdown != null) {
            Text(
                text = stringResource(R.string.cgm_warmup_remaining_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColumnScope.WarmupDetails(state: Libre3WarmupState, finished: Boolean) {
    // A landmark the user can plan around, shown whenever the driver knows it.
    state.endsAtEpochMs?.let { endsAt ->
        Text(
            text = stringResource(
                R.string.cgm_warmup_ends_at,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(endsAt)),
            ),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    when {
        state.phase == Libre3WarmupState.Phase.FAILED -> {
            CgmCard(tone = CgmCardTone.Warning) {
                CgmCardHeader(stringResource(R.string.cgm_warmup_what_happened))
                // The driver's own reason is English text for the log, so the screen shows a
                // translated sentence and points to the driver log for the details.
                Text(
                    text = stringResource(R.string.libre3_warmup_failed_detail),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        finished                                      -> {
            Text(
                text = stringResource(R.string.libre3_warmup_done),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        else                                          -> {
            Text(
                text = stringResource(R.string.libre3_warmup_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun Libre3WarmupScreenPreview() {
    MaterialTheme {
        Libre3WarmupScreen(onBack = {}, onOpenLog = {})
    }
}
