package app.aaps.plugins.source.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.CgmSensorLifecycle
import app.aaps.core.interfaces.source.CgmStagingEvidence
import app.aaps.core.interfaces.source.PromotionRejectReason
import app.aaps.core.interfaces.source.PromotionResult
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.libre3.Libre3CgmDriver
import app.aaps.plugins.libre3.Libre3CgmDrivers
import app.aaps.plugins.libre3.Libre3LogMarkers
import app.aaps.plugins.libre3.Libre3WarmupState
import app.aaps.plugins.libre3.identity.Libre3SensorStore
import app.aaps.plugins.source.Libre3Ingest
import app.aaps.plugins.source.Libre3NativePlugin
import app.aaps.plugins.source.Libre3PresoakPoint
import app.aaps.plugins.source.R
import app.aaps.plugins.source.compose.CgmCard
import app.aaps.plugins.source.compose.CgmCardHeader
import app.aaps.plugins.source.compose.CgmKeyValueRow
import app.aaps.plugins.source.compose.CgmLazyColumn
import app.aaps.plugins.source.compose.CgmScaffold
import app.aaps.plugins.source.compose.CgmStateChip
import app.aaps.plugins.source.compose.CgmWarmupRing
import app.aaps.plugins.source.compose.Libre3PresoakCurve
import app.aaps.plugins.source.compose.Libre3UiLabels
import app.aaps.plugins.source.compose.Libre3WarmupCountdown
import app.aaps.plugins.source.compose.formatSensorAge
import app.aaps.plugins.source.compose.toCgmWarmupInfo
import app.aaps.plugins.source.compose.toUiState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shows which sensor is stored and what the session is doing.
 *
 * One card for the sensor, one for the actions. The screen scrolls, so "Forget this sensor", the
 * only escape from a sensor that can never connect, is never pushed off the bottom of a short
 * screen.
 *
 * It is also the detail view of a pre-soak: the pre-soak warm-up notification opens this screen.
 */
class Libre3StatusActivity : MetroAppCompatActivity() {

    @Inject lateinit var preferences: Preferences

    @Inject lateinit var plugin: Libre3NativePlugin

    @Inject lateinit var profileUtil: ProfileUtil

    @Inject lateinit var dateUtil: DateUtil

    @Inject lateinit var rh: ResourceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Other apps may not draw over this screen: an overlay could hide what a tap really does, here on a
        // sensor (start, calibration, pre-soak). Same as ComposeMainActivity. Needs HIDE_OVERLAY_WINDOWS.
        window.setHideOverlayWindows(true)
        // Only on a fresh visit, never on a rebuild: the message has to survive a rotation, but an
        // answer from an hour ago must not greet the user when the screen is opened again.
        if (savedInstanceState == null) Libre3PresoakAction.clear()
        setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    Libre3StatusScreen(
                        onBack = { finish() },
                        onOpenLog = {
                            startActivity(CgmDriverLogActivity.intent(this, setOf(Libre3LogMarkers.TAG)))
                        },
                        onOpenStart = {
                            startActivity(Intent(this, Libre3StartActivity::class.java))
                        },
                        onConnect = { address -> plugin.connectStoredSensor(address) },
                        isPluginStopped = { plugin.isStopped() },
                        stagingStateFlow = plugin.stagingState,
                        stagingEvidenceFlow = plugin.stagingEvidence,
                        stagingLifecycleFlow = plugin.stagingLifecycle,
                        stagingCurveFlow = plugin.stagingCurve,
                        formatGlucose = { mgdl -> profileUtil.fromMgdlToStringWithUnits(mgdl) },
                        formatTime = { epochMs -> dateUtil.timeString(epochMs) },
                        formatAge = { millis -> formatSensorAge(millis, rh) },
                        // A pre-soak failure must never take production with it, so every staging
                        // call from this screen is wrapped here.
                        onPromote = { plugin.promoteStagingToProduction(allowEarly = true) },
                        onCancelStaging = { runCatching { plugin.cancelStaging() } },
                        onSensorForgotten = { plugin.refreshSessionService() },
                        presoakMessageFlow = Libre3PresoakAction.message,
                        runPresoakAction = { work -> Libre3PresoakAction.run(work) },
                    )
                }
            }
        }
    }
}

/**
 * What the screen says when `onPromote` throws before any [PromotionResult].
 *
 * The identity is read from the production store after the throw. A failure of that read is its
 * own sentence: the screen does not invent a serial, and it does not keep the previous message.
 */
private fun promotionThrew(context: Context, store: Libre3SensorStore): String {
    val read = runCatching { store.loadIdentity()?.serialNumber }
    return read.fold(
        onSuccess = { serial ->
            context.getString(
                R.string.libre3_presoak_promote_threw,
                serial ?: context.getString(R.string.libre3_presoak_promote_threw_none),
            )
        },
        onFailure = { context.getString(R.string.libre3_presoak_promote_threw_unreadable) },
    )
}

/**
 * Runs the pre-soak actions of the status screen and keeps the message the last one ended with.
 *
 * Promoting swaps the sensor that feeds the loop. A scope taken with `rememberCoroutineScope` is
 * cancelled the moment the composition goes away, so turning the phone while a promotion ran would
 * cut the swap in half and leave the user with no answer at all — on the one action that changes
 * where their insulin decisions come from. The work therefore runs here, outside the screen, and
 * the message is kept here too so a rebuilt screen shows it again.
 *
 * Process wide on purpose: anything owned by the activity dies with the activity, which is exactly
 * the problem. `Libre3StatusActivity.onCreate` empties it when the screen is opened fresh.
 *
 * A throw leaves the message untouched.
 */
internal object Libre3PresoakAction {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val state = MutableStateFlow<String?>(null)

    /** What the last finished action left to say, or null when there is nothing. */
    val message: StateFlow<String?> = state.asStateFlow()

    /** Forgets the last message. */
    fun clear() {
        state.value = null
    }

    /**
     * Runs one pre-soak action and publishes the message it returns.
     *
     * @param work does the work and returns the sentence to show. A throw leaves the message
     *   untouched instead of reaching the default handler: this scope outlives every screen, so an
     *   escaping error here would take the whole app down, and a pre-soak must never do that.
     */
    fun run(work: suspend () -> String) {
        scope.launch {
            runCatching { work() }.getOrNull()?.let { state.value = it }
        }
    }
}

@Composable
internal fun Libre3StatusScreen(
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenStart: () -> Unit,
    onConnect: (String) -> Unit,
    /** True while the plugin is switched off: Connect and Promote are then refused with a message. */
    isPluginStopped: () -> Boolean,
    stagingStateFlow: StateFlow<StagingState>,
    stagingEvidenceFlow: StateFlow<CgmStagingEvidence?>,
    stagingLifecycleFlow: StateFlow<CgmSensorLifecycle?>,
    stagingCurveFlow: StateFlow<List<Libre3PresoakPoint>>,
    formatGlucose: (Double) -> String,
    formatTime: (Long) -> String,
    formatAge: (Long) -> String,
    onPromote: suspend () -> PromotionResult,
    onCancelStaging: () -> Unit,
    onSensorForgotten: () -> Unit,
    presoakMessageFlow: StateFlow<String?>,
    runPresoakAction: (suspend () -> String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The production driver, asked again at every use. A promotion or a plugin restart replaces
     * the instance, so one kept from the first composition would be a retired driver.
     */
    currentDriver: () -> Libre3CgmDriver = { Libre3CgmDrivers.default() },
) {
    val context = LocalContext.current
    val store = remember { Libre3SensorStore(context) }
    var identity by remember { mutableStateOf(store.loadIdentity()) }
    var phase by remember { mutableStateOf(currentDriver().warmupState().phase) }
    var sessionUp by remember { mutableStateOf(currentDriver().isSessionUp()) }
    var connectRefused by rememberSaveable { mutableStateOf(false) }
    var askingToForget by rememberSaveable { mutableStateOf(false) }
    var forgotten by rememberSaveable { mutableStateOf(false) }
    var askingToPromote by rememberSaveable { mutableStateOf(false) }
    var askingToCancelPresoak by rememberSaveable { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var stagingWarmup by remember { mutableStateOf<Libre3WarmupState?>(null) }

    val stagingState by stagingStateFlow.collectAsStateWithLifecycle()
    val stagingEvidence by stagingEvidenceFlow.collectAsStateWithLifecycle()
    val stagingLifecycle by stagingLifecycleFlow.collectAsStateWithLifecycle()
    val stagingCurve by stagingCurveFlow.collectAsStateWithLifecycle()
    val presoakResultText by presoakMessageFlow.collectAsStateWithLifecycle()

    val promoteOk = stringResource(R.string.libre3_presoak_promote_ok)
    val promoteBoundFailed = stringResource(R.string.libre3_presoak_promote_bound_failed)
    val promoteCheckState = stringResource(R.string.libre3_presoak_promote_check_state)
    val promoteFollowUpNoIdentity = stringResource(R.string.libre3_presoak_promote_follow_up_no_identity)
    val promoteRejectedAbsent = stringResource(R.string.libre3_presoak_promote_rejected_absent)
    val promoteRejectedOther = stringResource(R.string.libre3_presoak_promote_rejected_other)
    val presoakCancelled = stringResource(R.string.libre3_presoak_cancel_done)
    val promotePluginStopped = stringResource(R.string.libre3_plugin_stopped)

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                identity = store.loadIdentity()
                val current = currentDriver()
                phase = current.warmupState().phase
                sessionUp = current.isSessionUp()
                delay(2_000L)
            }
        }
    }

    LaunchedEffect(lifecycleOwner, stagingState) {
        if (stagingState == StagingState.ABSENT) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMs = System.currentTimeMillis()
                stagingWarmup = runCatching { Libre3CgmDrivers.stagingOrNull()?.warmupState() }.getOrNull()
                delay(1_000L)
            }
        }
    }

    CgmScaffold(
        title = stringResource(R.string.libre3_status_title),
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
    ) {
        CgmLazyColumn {
            item(key = "sensor") {
                val stored = identity
                CgmCard(accent = stored != null) {
                    CgmCardHeader(stringResource(R.string.libre3_status_heading)) {
                        CgmStateChip(state = phase.toUiState(), label = Libre3UiLabels.phaseLabel(phase))
                    }
                    if (stored == null) {
                        Text(
                            text = stringResource(R.string.libre3_status_no_sensor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = onOpenStart) {
                            Text(stringResource(R.string.libre3_status_open_start))
                        }
                    } else {
                        CgmKeyValueRow(
                            label = stringResource(R.string.libre3_label_serial),
                            value = stored.serialNumber,
                        )
                        CgmKeyValueRow(
                            label = stringResource(R.string.libre3_label_family),
                            value = Libre3UiLabels.generationLabel(stored.generation),
                        )
                        CgmKeyValueRow(
                            label = stringResource(R.string.cgm_sensor_address),
                            value = stored.bleAddress,
                        )
                        CgmKeyValueRow(
                            label = stringResource(R.string.cgm_session_label),
                            value = stringResource(
                                if (sessionUp) R.string.libre3_status_session_up else R.string.libre3_status_session_down,
                            ),
                        )
                        Button(
                            onClick = {
                                // Connect goes through the plugin, which refuses and logs it when this
                                // build cannot pair.
                                val action = libre3SessionButtonClick(
                                    sessionUp = sessionUp,
                                    pluginStopped = isPluginStopped(),
                                    currentDriver = currentDriver,
                                    connect = { onConnect(stored.bleAddress) },
                                )
                                connectRefused = action == Libre3SessionButtonAction.REFUSED_PLUGIN_STOPPED
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if (sessionUp) R.string.libre3_status_disconnect else R.string.libre3_status_connect
                                )
                            )
                        }
                        if (connectRefused && !sessionUp) {
                            Text(
                                text = stringResource(R.string.libre3_plugin_stopped),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(
                            onClick = { askingToForget = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.libre3_forget_sensor))
                        }
                    }
                }
            }

            if (stagingState != StagingState.ABSENT) {
                item(key = "presoak") {
                    PresoakCard(
                        stagingState = stagingState,
                        stagingEvidence = stagingEvidence,
                        stagingLifecycle = stagingLifecycle,
                        stagingWarmup = stagingWarmup,
                        curve = stagingCurve,
                        nowMs = nowMs,
                        formatGlucose = formatGlucose,
                        formatTime = formatTime,
                        formatAge = formatAge,
                        onPromoteClick = { askingToPromote = true },
                        onCancelClick = { askingToCancelPresoak = true },
                    )
                }
            }

            presoakResultText?.let { message ->
                item(key = "presoakResult") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (forgotten) {
                item(key = "forgotten") {
                    Text(
                        text = stringResource(R.string.libre3_forget_sensor_done),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "actions") {
                CgmCard {
                    CgmCardHeader(stringResource(R.string.cgm_actions_heading))
                    OutlinedButton(onClick = onOpenStart, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.libre3_status_open_start))
                    }
                    OutlinedButton(onClick = onOpenLog, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cgm_driver_log_open))
                    }
                }
            }
        }
    }

    if (askingToForget) {
        AlertDialog(
            onDismissRequest = { askingToForget = false },
            title = { Text(stringResource(R.string.libre3_forget_sensor_title)) },
            text = { Text(stringResource(R.string.libre3_forget_sensor_explain)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askingToForget = false
                        forgetSensor(currentDriver(), store)
                        identity = null
                        sessionUp = false
                        forgotten = true
                        onSensorForgotten()
                    }
                ) {
                    Text(stringResource(R.string.libre3_forget_sensor_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { askingToForget = false }) {
                    Text(stringResource(R.string.libre3_forget_sensor_cancel))
                }
            },
        )
    }

    // Promotion swaps the sensor that feeds the loop, so it is never one tap away.
    if (askingToPromote) {
        AlertDialog(
            onDismissRequest = { askingToPromote = false },
            title = { Text(stringResource(R.string.libre3_presoak_promote_title)) },
            text = { Text(stringResource(R.string.libre3_presoak_promote_explain)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askingToPromote = false
                        // Run outside the composition, so turning the phone during the swap
                        // neither cuts it in half nor swallows the answer.
                        runPresoakAction {
                            if (isPluginStopped()) return@runPresoakAction promotePluginStopped
                            // A throw before any PromotionResult must not keep the previous message.
                            // The sentence is the failure, then the production identity read afterwards.
                            when (val result = runCatching { onPromote() }.getOrNull()) {
                                PromotionResult.Ok                  -> promoteOk
                                PromotionResult.OkBoundFailed       -> promoteBoundFailed
                                is PromotionResult.OkFollowUpFailed ->
                                    if (result.productionIdentityPresent) promoteCheckState
                                    else promoteFollowUpNoIdentity
                                is PromotionResult.Rejected         -> when (result.reason) {
                                    PromotionRejectReason.STAGING_ABSENT -> promoteRejectedAbsent
                                    else                                 -> promoteRejectedOther
                                }
                                null                                -> promotionThrew(context, store)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.libre3_presoak_promote_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { askingToPromote = false }) {
                    Text(stringResource(R.string.libre3_presoak_promote_cancel))
                }
            },
        )
    }

    if (askingToCancelPresoak) {
        AlertDialog(
            onDismissRequest = { askingToCancelPresoak = false },
            title = { Text(stringResource(R.string.libre3_presoak_cancel_title)) },
            text = { Text(stringResource(R.string.libre3_presoak_cancel_explain)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askingToCancelPresoak = false
                        runPresoakAction {
                            onCancelStaging()
                            presoakCancelled
                        }
                    }
                ) {
                    Text(stringResource(R.string.libre3_presoak_cancel_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { askingToCancelPresoak = false }) {
                    Text(stringResource(R.string.libre3_presoak_cancel_dismiss))
                }
            },
        )
    }
}

@Composable
private fun PresoakCard(
    stagingState: StagingState,
    stagingEvidence: CgmStagingEvidence?,
    stagingLifecycle: CgmSensorLifecycle?,
    stagingWarmup: Libre3WarmupState?,
    curve: List<Libre3PresoakPoint>,
    nowMs: Long,
    formatGlucose: (Double) -> String,
    formatTime: (Long) -> String,
    formatAge: (Long) -> String,
    onPromoteClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    val stateLabel = when (stagingState) {
        StagingState.ABSENT   -> stringResource(R.string.libre3_presoak_state_absent)
        StagingState.WARMUP   -> stringResource(R.string.libre3_presoak_state_warmup)
        StagingState.SETTLING -> stringResource(R.string.libre3_presoak_state_settling)
        StagingState.READY    -> stringResource(R.string.libre3_presoak_state_ready)
    }
    CgmCard {
        CgmCardHeader(stringResource(R.string.libre3_presoak_heading)) {
            CgmStateChip(state = stagingState.toUiState(), label = stateLabel)
        }
        if (stagingState == StagingState.WARMUP) {
            val remainingMs = stagingWarmup?.let {
                Libre3WarmupCountdown.remainingMs(it.toCgmWarmupInfo(), nowMs)
            }
            CgmWarmupRing(
                progress = null,
                state = stagingState.toUiState(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = remainingMs?.let { Libre3WarmupCountdown.format(it) }
                        ?: stringResource(R.string.libre3_warmup_countdown_unknown),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Text(
                text = stringResource(R.string.libre3_presoak_warmup_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Libre3PresoakCurve(points = curve, nowMs = nowMs, formatGlucose = formatGlucose)
        }
        val soakMs = stagingLifecycle?.let { lifecycle ->
            lifecycle.startedAtEpochMs?.let { startedAt -> (nowMs - startedAt).coerceAtLeast(0L) }
                ?: lifecycle.ageMs
        }
        soakMs?.let { millis ->
            CgmKeyValueRow(
                label = stringResource(R.string.libre3_presoak_soak_time),
                value = formatAge(millis),
            )
        }
        CgmKeyValueRow(
            label = stringResource(R.string.libre3_presoak_reading_count),
            value = (stagingEvidence?.validCount ?: 0).toString(),
        )
        val lastValueMgdl = stagingEvidence?.lastValueMgdl
        val lastValueAtMs = stagingEvidence?.lastValueAtEpochMs
        if (lastValueMgdl != null && lastValueAtMs != null) {
            CgmKeyValueRow(
                label = stringResource(R.string.libre3_presoak_last_value),
                value = stringResource(
                    R.string.libre3_presoak_last_value_at,
                    formatGlucose(lastValueMgdl),
                    formatTime(lastValueAtMs),
                ),
            )
        }
        Text(
            text = stringResource(R.string.libre3_presoak_info_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPromoteClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.libre3_presoak_promote))
        }
        TextButton(onClick = onCancelClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.libre3_presoak_cancel))
        }
    }
}

/** What a tap on the Connect / Disconnect button of the status screen did. */
internal enum class Libre3SessionButtonAction { DISCONNECTED, CONNECT_ASKED, REFUSED_PLUGIN_STOPPED }

/**
 * The Connect / Disconnect button of the status screen.
 *
 * The driver is asked for at the tap, never kept: after a promotion or a plugin restart the
 * instance from the first composition is retired, and a disconnect sent to it would leave the
 * real session running.
 */
internal fun libre3SessionButtonClick(
    sessionUp: Boolean,
    pluginStopped: Boolean,
    currentDriver: () -> Libre3CgmDriver,
    connect: () -> Unit,
): Libre3SessionButtonAction = when {
    sessionUp     -> {
        currentDriver().disconnect()
        Libre3SessionButtonAction.DISCONNECTED
    }

    pluginStopped -> Libre3SessionButtonAction.REFUSED_PLUGIN_STOPPED

    else          -> {
        connect()
        Libre3SessionButtonAction.CONNECT_ASKED
    }
}

private fun forgetSensor(driver: Libre3CgmDriver, store: Libre3SensorStore) {
    driver.disconnect()
    store.clear()
    Libre3Ingest.reset()
}

@Preview
@Composable
private fun Libre3StatusScreenPreview() {
    MaterialTheme {
        Libre3StatusScreen(
            onBack = {},
            onOpenLog = {},
            onOpenStart = {},
            onConnect = {},
            isPluginStopped = { false },
            stagingStateFlow = MutableStateFlow(StagingState.ABSENT),
            stagingEvidenceFlow = MutableStateFlow(null),
            stagingLifecycleFlow = MutableStateFlow(null),
            stagingCurveFlow = MutableStateFlow(emptyList()),
            formatGlucose = { mgdl -> mgdl.toString() },
            formatTime = { epochMs -> epochMs.toString() },
            formatAge = { millis -> millis.toString() },
            onPromote = { PromotionResult.Rejected(PromotionRejectReason.STAGING_ABSENT) },
            onCancelStaging = {},
            onSensorForgotten = {},
            presoakMessageFlow = MutableStateFlow(null),
            runPresoakAction = {},
        )
    }
}
