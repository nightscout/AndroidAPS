package app.aaps.plugins.source.compose

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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDrivers
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import app.aaps.plugins.source.SourceStrings
import kotlinx.coroutines.delay
import kotlin.time.Clock

/**
 * Warm-up countdown screen bound to [OnePlusWarmupState].
 *
 * Countdown source (see [DexcomOnePlusWarmupCountdown]):
 * - Prefer protocol `remainingMs` / `endsAtEpochMs`.
 * - Local ~30 min fallback timer ONLY when both are null and phase is WARMING.
 *
 * Layout follows the window: in portrait the ring is the subject of the screen and the details sit
 * under it; on a short screen (phone in landscape) the ring moves beside the details, which is the
 * only arrangement that fits about 360 dp of height without cutting anything off.
 *
 * The three ways to another screen are nullable. A phone that has no such screen passes null, and
 * the button is not drawn, rather than drawn and doing nothing.
 *
 * @param storedSession reads the production slot's stored sensor once, for the sensor card.
 * @param onRetry starts the stored session again after a failed warm-up.
 * @param formatTime the time of day of an epoch millisecond value, in the phone's own format.
 */
@Composable
fun DexcomOnePlusWarmupScreen(
    storedSession: () -> OnePlusStoredSession?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenLog: (() -> Unit)?,
    onOpenStart: (() -> Unit)?,
    onOpenStatus: (() -> Unit)?,
    formatTime: (Long) -> String,
) {
    val window = rememberCgmWindow()
    // Identity is read once: it only changes when a sensor is started, which leaves this screen.
    val session = remember { storedSession() }
    // Not `remember`-ed: a promotion swaps which driver instance `default()` hands out (see
    // OnePlusCgmDrivers.promoteStagingInstance), and a `remember`-ed reference kept polling the
    // retired instance for the rest of this screen's life.
    var state by remember { mutableStateOf(OnePlusCgmDrivers.default().warmupState()) }
    var sessionUp by remember { mutableStateOf(OnePlusCgmDrivers.default().isSessionUp()) }
    var localFallbackEndsAt by remember { mutableStateOf<Long?>(null) }
    var remainingMs by remember { mutableStateOf<Long?>(null) }
    var usingLocalFallback by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val driver = OnePlusCgmDrivers.default()
            val now = Clock.System.now().toEpochMilliseconds()
            state = driver.warmupState()
            val info = state.toCgmWarmupInfo()
            sessionUp = driver.isSessionUp()
            if (DexcomOnePlusWarmupCountdown.shouldStartLocalFallback(info) && localFallbackEndsAt == null) {
                // Local fallback ONLY if remainingMs (and endsAt) are null — documented above.
                localFallbackEndsAt = now + DexcomOnePlusWarmupCountdown.LOCAL_FALLBACK_DURATION_MS
            }
            if (DexcomOnePlusWarmupCountdown.shouldClearLocalFallback(info.phase)) {
                localFallbackEndsAt = null
            }
            usingLocalFallback =
                state.remainingMs == null &&
                    state.endsAtEpochMs == null &&
                    localFallbackEndsAt != null &&
                    state.phase == OnePlusWarmupState.Phase.WARMING
            remainingMs = DexcomOnePlusWarmupCountdown.resolveRemainingMs(
                state = info,
                nowEpochMs = now,
                localFallbackEndsAtEpochMs = localFallbackEndsAt,
            )
            delay(1_000L)
        }
    }

    val model = WarmupUiModel(
        state = state,
        sessionUp = sessionUp,
        remainingMs = remainingMs,
        localFallbackEndsAt = localFallbackEndsAt,
        usingLocalFallback = usingLocalFallback,
    )
    val links = WarmupLinks(
        onRetry = onRetry,
        onOpenLog = onOpenLog,
        onOpenStart = onOpenStart,
        onOpenStatus = onOpenStatus,
    )

    CgmScaffold(
        title = stringResource(SourceStrings.dexcom_oneplus_warmup_title),
        onNavigate = onBack,
        actions = {
            if (onOpenLog != null) {
                IconButton(onClick = onOpenLog) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(SourceStrings.cgm_driver_log_open),
                    )
                }
            }
        },
        // The short layout places the ring beside the text and manages its own width.
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
                    progress = ringProgress(model),
                    state = model.state.phase.toUiState(),
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                ) {
                    RingCenter(model)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.large),
                ) {
                    WarmupDetails(
                        model = model,
                        deviceName = session?.lastDeviceName,
                        deviceAddress = session?.lastMac,
                        compactHeadline = true,
                        links = links,
                        formatTime = formatTime,
                    )
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
                        StateChips(model)
                        // Width only: the box wraps the ring's own height, so the ring is not
                        // parked in the middle of a full width square of empty space.
                        CgmWarmupRing(
                            progress = ringProgress(model),
                            state = model.state.phase.toUiState(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RingCenter(model)
                        }
                    }
                }
                item(key = "details") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large),
                    ) {
                        WarmupDetails(
                            model = model,
                            deviceName = session?.lastDeviceName,
                            deviceAddress = session?.lastMac,
                            compactHeadline = false,
                            links = links,
                            formatTime = formatTime,
                        )
                    }
                }
            }
        }
    }
}

/** Everything the screen shows, gathered once per tick so both layouts read the same values. */
private data class WarmupUiModel(
    val state: OnePlusWarmupState,
    val sessionUp: Boolean,
    val remainingMs: Long?,
    val localFallbackEndsAt: Long?,
    val usingLocalFallback: Boolean,
)

/** The ways out of this screen. A null entry is a screen this phone does not have. */
private class WarmupLinks(
    val onRetry: () -> Unit,
    val onOpenLog: (() -> Unit)?,
    val onOpenStart: (() -> Unit)?,
    val onOpenStatus: (() -> Unit)?,
)

/**
 * Determinate only when the local ~30 min fallback owns the clock, because only then is the total
 * known. Otherwise a full ring signals honest, open-ended progress instead of inventing a fraction.
 */
private fun ringProgress(model: WarmupUiModel): Float? =
    if (model.state.phase == OnePlusWarmupState.Phase.WARMING && model.usingLocalFallback) {
        val remaining = model.remainingMs ?: 0L
        (1f - remaining.toFloat() / DexcomOnePlusWarmupCountdown.LOCAL_FALLBACK_DURATION_MS)
            .coerceIn(0f, 1f)
    } else {
        null
    }

@Composable
private fun RingCenter(model: WarmupUiModel) {
    // Keep the mm:ss countdown while the link is being re-established: a normal duty-cycle
    // disconnect during warm-up used to blank it until the next EGV packet.
    val countdownText = model.remainingMs?.let { DexcomOnePlusWarmupCountdown.formatMmSs(it) }
    val warmupClockPhase = model.state.phase == OnePlusWarmupState.Phase.WARMING ||
        model.state.phase == OnePlusWarmupState.Phase.IDLE ||
        model.state.phase == OnePlusWarmupState.Phase.PAIRING
    val phase = model.state.phase.toCgmWarmupPhase()
    val centerText = when {
        DexcomOnePlusWarmupCountdown.showsCountdown(phase) && countdownText != null -> countdownText
        warmupClockPhase                                                            ->
            stringResource(SourceStrings.dexcom_oneplus_warmup_countdown_unknown)

        else                                                                        ->
            DexcomOnePlusUiLabels.phaseLabel(model.state.phase)
    }
    val showsRemainingLabel = countdownText != null &&
        DexcomOnePlusWarmupCountdown.showsCountdown(phase)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall),
    ) {
        Text(
            text = centerText,
            style = MaterialTheme.typography.headlineMedium,
            color = if (model.state.phase == OnePlusWarmupState.Phase.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
        )
        if (showsRemainingLabel) {
            Text(
                text = stringResource(SourceStrings.cgm_warmup_remaining_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StateChips(model: WarmupUiModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        CgmStateChip(
            state = model.state.phase.toUiState(),
            label = DexcomOnePlusUiLabels.phaseLabel(model.state.phase),
        )
        CgmStateChip(
            state = if (model.sessionUp) CgmUiState.Ready else CgmUiState.Waiting,
            label = stringResource(
                if (model.sessionUp) SourceStrings.dexcom_oneplus_session_up else SourceStrings.dexcom_oneplus_session_down,
            ),
        )
    }
}

@Composable
private fun ColumnScope.WarmupDetails(
    model: WarmupUiModel,
    deviceName: String?,
    deviceAddress: String?,
    compactHeadline: Boolean,
    links: WarmupLinks,
    formatTime: (Long) -> String,
) {
    if (compactHeadline) StateChips(model)

    // Honest end-of-warm-up annotation, from the protocol clock or the local fallback. Shown during
    // the connection phases too, so the user keeps a landmark while the sensor's radio window is
    // closed.
    val endMs = model.state.endsAtEpochMs ?: model.localFallbackEndsAt
    endMs?.let {
        Text(
            text = stringResource(SourceStrings.cgm_warmup_ends_at, formatTime(it)),
            style = MaterialTheme.typography.titleMedium,
        )
    }

    when (model.state.phase) {
        OnePlusWarmupState.Phase.READY  -> {
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_warmup_done),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        OnePlusWarmupState.Phase.FAILED -> {
            // An interrupted warm-up used to be a red line and nothing else. These are the three
            // ways out that actually exist, in the order they are worth trying.
            CgmCard(tone = CgmCardTone.Warning) {
                CgmCardHeader(stringResource(SourceStrings.cgm_warmup_what_happened))
                Text(
                    text = DexcomOnePlusUiLabels.userMessage(model.state.message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = links.onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(SourceStrings.dexcom_oneplus_warmup_retry))
            }
            links.onOpenLog?.let { openLog ->
                OutlinedButton(onClick = openLog, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(SourceStrings.cgm_driver_log_open))
                }
            }
            links.onOpenStart?.let { openStart ->
                TextButton(onClick = openStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(SourceStrings.dexcom_oneplus_warmup_start_other))
                }
            }
        }

        OnePlusWarmupState.Phase.CONNECTING,
        OnePlusWarmupState.Phase.RECONNECTING -> {
            // Link is being (re)established — reassure, don't show a fake countdown or the terminal
            // red FAILED that used to flash between retries.
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_warmup_connecting_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else                            -> {
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_warmup_no_loop_bg),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Typed driver signals the old screen carried but never showed: how long the sensor has been
    // silent, and whether the sensor answering nearby is not the paired one at all.
    model.state.advSilenceMinutes?.let { minutes ->
        Text(
            text = stringResource(SourceStrings.dexcom_oneplus_notif_waiting_adv, minutes.toInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (model.state.staleMacSuspected) {
        CgmCard(tone = CgmCardTone.Warning) {
            CgmCardHeader(stringResource(SourceStrings.dexcom_oneplus_notif_stale_mac_title))
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_notif_stale_mac_text),
                style = MaterialTheme.typography.bodyMedium,
            )
            links.onOpenStart?.let { openStart ->
                OutlinedButton(onClick = openStart) {
                    Text(stringResource(SourceStrings.dexcom_oneplus_warmup_start_other))
                }
            }
        }
    }

    // Which sensor this countdown belongs to — a real question once a pre-soak sensor can be
    // warming up at the same time.
    if (deviceName != null || deviceAddress != null) {
        CgmCard {
            CgmCardHeader(stringResource(SourceStrings.cgm_sensor_heading))
            deviceName?.let {
                CgmKeyValueRow(stringResource(SourceStrings.cgm_sensor_name), it)
            }
            deviceAddress?.let {
                CgmKeyValueRow(stringResource(SourceStrings.cgm_sensor_address), it)
            }
            if (model.usingLocalFallback) {
                Text(
                    text = stringResource(SourceStrings.dexcom_oneplus_warmup_local_fallback_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (model.state.phase != OnePlusWarmupState.Phase.FAILED &&
        (links.onOpenLog != null || links.onOpenStatus != null)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        ) {
            links.onOpenLog?.let { openLog ->
                OutlinedButton(onClick = openLog, modifier = Modifier.weight(1f)) {
                    Text(stringResource(SourceStrings.cgm_driver_log_open))
                }
            }
            links.onOpenStatus?.let { openStatus ->
                OutlinedButton(onClick = openStatus, modifier = Modifier.weight(1f)) {
                    Text(stringResource(SourceStrings.dexcom_oneplus_open_status))
                }
            }
        }
    }
}
