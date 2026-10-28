package app.aaps.plugins.source.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.CgmSensorLifecycle
import app.aaps.core.interfaces.source.CgmStagingEvidence
import app.aaps.core.interfaces.source.PromotionRejectReason
import app.aaps.core.interfaces.source.PromotionResult
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.core.ui.compose.dialogs.DatePickerModal
import app.aaps.core.ui.compose.dialogs.TimePickerModal
import app.aaps.core.ui.compose.stringResource as coreStringResource
import app.aaps.plugins.dexcomoneplus.OnePlusCalibrationOutcome
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDrivers
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrationState
import app.aaps.plugins.source.DexcomOnePlusCalibrationText
import app.aaps.plugins.source.DexcomOnePlusPlugin
import app.aaps.plugins.source.DexcomOnePlusSensorStartCorrection
import app.aaps.plugins.source.DexcomOnePlusStaging
import app.aaps.plugins.source.R
import app.aaps.plugins.source.keys.DexcomOnePlusBooleanKey
import app.aaps.plugins.source.compose.CgmCard
import app.aaps.plugins.source.compose.CgmCardHeader
import app.aaps.plugins.source.compose.CgmCardTone
import app.aaps.plugins.source.compose.CgmKeyValueRow
import app.aaps.plugins.source.compose.CgmLazyColumn
import app.aaps.plugins.source.compose.CgmScaffold
import app.aaps.plugins.source.compose.CgmStateChip
import app.aaps.plugins.source.compose.CgmStepTimeline
import app.aaps.plugins.source.compose.CgmUiState
import app.aaps.plugins.source.compose.DexcomOnePlusProductionCard
import app.aaps.plugins.source.compose.DexcomOnePlusUiLabels
import app.aaps.plugins.source.compose.formatSensorAge
import app.aaps.plugins.source.compose.toUiState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/**
 * Daily status of the native Dexcom ONE+ / G7 source.
 *
 * The screen answers two questions that used to share one flat list of text lines: what is the
 * sensor feeding the loop doing, and what is the pre-soak sensor doing. Each gets its own card, so
 * a reading belongs to a visible subject. Everything sits in one scrolling list — the old
 * non-scrolling column pushed the promote button, the one action that switches the loop's glucose
 * source, past the bottom edge as soon as a staging sensor was present.
 */
class DexcomOnePlusStatusActivity : MetroAppCompatActivity() {

    @Inject lateinit var dexcomOnePlusPlugin: DexcomOnePlusPlugin
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var persistenceLayer: PersistenceLayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Other apps may not draw over this screen: an overlay could hide what a tap really does, here on a
        // sensor (start, calibration, pre-soak, switch). Same as ComposeMainActivity. Needs HIDE_OVERLAY_WINDOWS.
        window.setHideOverlayWindows(true)
        dexcomOnePlusPlugin.syncDriverFromPrefs()
        setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    DexcomOnePlusStatusScreen(
                        onBack = { finish() },
                        onOpenStart = {
                            startActivity(Intent(this, DexcomOnePlusStartActivity::class.java))
                        },
                        onOpenWarmup = {
                            startActivity(Intent(this, DexcomOnePlusWarmupActivity::class.java))
                        },
                        onOpenLog = {
                            startActivity(
                                Intent(this, CgmDriverLogActivity::class.java)
                            )
                        },
                        stagingStateFlow = dexcomOnePlusPlugin.stagingState,
                        stagingEvidenceFlow = dexcomOnePlusPlugin.stagingEvidence,
                        lifecycleFlow = dexcomOnePlusPlugin.lifecycle,
                        formatGlucose = { mgdl -> profileUtil.fromMgdlToStringWithUnits(mgdl) },
                        formatTime = { epochMs -> dateUtil.timeString(epochMs) },
                        formatAge = { millis -> formatSensorAge(millis, rh) },
                        lastGlucose = { persistenceLayer.getLastGlucoseValue() },
                        onCancelStaging = { dexcomOnePlusPlugin.cancelStaging() },
                        onPromote = { allowEarly -> dexcomOnePlusPlugin.promoteStagingToProduction(allowEarly) },
                        onCorrectSensorStart = { startMs -> dexcomOnePlusPlugin.correctProductionSensorStart(startMs) },
                    )
                }
            }
        }
    }
}

/** How often the screen goes back to the database for the newest reading. */
private const val GLUCOSE_REFRESH_MILLIS = 30_000L

/**
 * Text shown after Promote. Refusal means nothing was written. [PromotionResult.OkBoundFailed]
 * means the exchange is kept and the calibration bound threw. [PromotionResult.OkFollowUpFailed]
 * means a later step threw; the wording follows [PromotionResult.OkFollowUpFailed.productionIdentityPresent],
 * which the plugin read from the production store.
 */
internal fun dexcomOnePlusPromotionMessage(
    result: PromotionResult,
    ok: String,
    boundFailed: String,
    followUpIdentityPresent: String,
    followUpIdentityMissing: String,
    rejectedAbsent: String,
    rejectedNotSettled: String,
    rejectedNoGlucose: String,
    rejectedNoRecentGlucose: String,
): String = when (result) {
    PromotionResult.Ok -> ok
    PromotionResult.OkBoundFailed -> boundFailed
    is PromotionResult.OkFollowUpFailed ->
        if (result.productionIdentityPresent) followUpIdentityPresent else followUpIdentityMissing
    is PromotionResult.Rejected -> when (result.reason) {
        PromotionRejectReason.STAGING_ABSENT -> rejectedAbsent
        PromotionRejectReason.STAGING_NOT_SETTLED -> rejectedNotSettled
        PromotionRejectReason.STAGING_NO_VALID_GLUCOSE -> rejectedNoGlucose
        PromotionRejectReason.STAGING_NO_RECENT_GLUCOSE -> rejectedNoRecentGlucose
    }
}

@Composable
private fun DexcomOnePlusStatusScreen(
    onBack: () -> Unit,
    onOpenStart: () -> Unit,
    onOpenWarmup: () -> Unit,
    onOpenLog: () -> Unit,
    stagingStateFlow: StateFlow<StagingState>,
    stagingEvidenceFlow: StateFlow<CgmStagingEvidence?>,
    lifecycleFlow: StateFlow<CgmSensorLifecycle?>,
    formatGlucose: (Double) -> String,
    formatTime: (Long) -> String,
    formatAge: (Long) -> String,
    lastGlucose: suspend () -> GV?,
    onCancelStaging: () -> Unit,
    onPromote: suspend (Boolean) -> PromotionResult,
    onCorrectSensorStart: suspend (Long) -> DexcomOnePlusSensorStartCorrection.Verdict,
) {
    // Not `remember`-ed: a promotion swaps which driver instance `default()` hands out (see
    // OnePlusCgmDrivers.promoteStagingInstance), and a `remember`-ed reference kept polling the
    // retired instance for the rest of this screen's life, showing a status stuck at whatever
    // phase it had before the promotion.
    var state by remember { mutableStateOf(OnePlusCgmDrivers.default().warmupState()) }
    var sessionUp by remember { mutableStateOf(OnePlusCgmDrivers.default().isSessionUp()) }
    var newestGlucose by remember { mutableStateOf<GV?>(null) }
    var calibrationOutcome by remember { mutableStateOf<OnePlusCalibrationOutcome?>(null) }
    var calibrationWaiting by remember { mutableStateOf(false) }
    // The card exists only while values are sent to the sensor. With the setting off the value
    // never leaves the phone, so there is nothing to report. The switch is on by default.
    val preferences = LocalPreferences.current
    val sendCalibrationToSensor = remember(preferences) {
        preferences.get(DexcomOnePlusBooleanKey.SendCalibrationToSensor)
    }
    val correctSensorStartEnabled = preferences.get(DexcomOnePlusBooleanKey.CorrectSensorStart)
    val stagingState by stagingStateFlow.collectAsState()
    val stagingEvidence by stagingEvidenceFlow.collectAsState()
    val lifecycle by lifecycleFlow.collectAsState()
    val scope = rememberCoroutineScope()
    var showPromoteConfirm by remember { mutableStateOf(false) }
    var promoteEarly by remember { mutableStateOf(false) }
    var promoteResultText by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    // Offer the early way out only while the sensor is proving itself right now — see canPromoteEarly.
    val earlyPromoteOffered = stagingState != StagingState.ABSENT && DexcomOnePlusStaging.canPromoteEarly(
        validEgvCount = stagingEvidence?.validCount ?: 0,
        lastValueAtEpochMs = stagingEvidence?.lastValueAtEpochMs,
        nowMs = now,
    )

    // Promotion result → user message (resolved here so the coroutine has no Composable context).
    val promoteOk = stringResource(R.string.dexcom_oneplus_staging_promote_ok)
    val promoteBoundFailed = stringResource(R.string.dexcom_oneplus_staging_promote_bound_failed)
    val promoteFollowUpFailed = stringResource(R.string.dexcom_oneplus_staging_promote_follow_up_failed)
    val promoteFollowUpNoIdentity = stringResource(R.string.dexcom_oneplus_staging_promote_follow_up_failed_no_identity)
    val promoteRejectedAbsent = stringResource(R.string.dexcom_oneplus_staging_promote_rejected_absent)
    val promoteRejectedNotSettled = stringResource(R.string.dexcom_oneplus_staging_promote_rejected_not_settled)
    val promoteRejectedNoGlucose = stringResource(R.string.dexcom_oneplus_staging_promote_rejected_no_glucose)
    val promoteRejectedNoRecentGlucose = stringResource(R.string.dexcom_oneplus_staging_promote_rejected_no_recent_glucose)

    LaunchedEffect(Unit) {
        while (true) {
            val driver = OnePlusCgmDrivers.default()
            state = driver.warmupState()
            sessionUp = driver.isSessionUp()
            calibrationOutcome = driver.lastCalibrationOutcome()
            calibrationWaiting = driver.calibrationPending()
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    // A reading arrives every five minutes, so the database is asked far less often than the driver
    // state is polled.
    LaunchedEffect(Unit) {
        while (true) {
            newestGlucose = lastGlucose()
            delay(GLUCOSE_REFRESH_MILLIS)
        }
    }

    CgmScaffold(
        title = stringResource(R.string.dexcom_oneplus_status_title),
        onNavigate = onBack,
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
            item(key = "production") {
                ProductionCard(
                    phaseLabel = DexcomOnePlusUiLabels.phaseLabel(state.phase),
                    phaseState = state.phase.toUiState(),
                    sessionUp = sessionUp,
                    message = DexcomOnePlusUiLabels.userMessage(state.message),
                    lifecycle = lifecycle,
                    newestGlucose = newestGlucose,
                    formatGlucose = formatGlucose,
                    formatTime = formatTime,
                    formatAge = formatAge,
                    correctSensorStartEnabled = correctSensorStartEnabled,
                    onCorrectSensorStart = onCorrectSensorStart,
                )
            }
            if (sendCalibrationToSensor) {
                item(key = "sensorCalibration") {
                    SensorCalibrationCard(
                        outcome = calibrationOutcome,
                        waiting = calibrationWaiting,
                        driverMessage = state.message,
                    )
                }
            }
            // Prompt for a pre-soak exactly when it is useful: the sensor in use is near its end and
            // no replacement is warming up yet.
            if (lifecycle?.endOfLife == true && stagingState == StagingState.ABSENT) {
                item(key = "endOfLife") {
                    CgmCard(tone = CgmCardTone.Warning) {
                        CgmCardHeader(stringResource(R.string.dexcom_oneplus_end_of_life_title))
                        Text(
                            text = stringResource(R.string.dexcom_oneplus_end_of_life_text),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = onOpenStart) {
                            Text(stringResource(R.string.dexcom_oneplus_start_presoak))
                        }
                    }
                }
            }
            item(key = "staging") {
                StagingCard(
                    stagingState = stagingState,
                    stagingEvidence = stagingEvidence,
                    earlyPromoteOffered = earlyPromoteOffered,
                    formatGlucose = formatGlucose,
                    formatTime = formatTime,
                    onPromoteClick = { early ->
                        promoteEarly = early
                        showPromoteConfirm = true
                    },
                    onCancelStaging = {
                        onCancelStaging()
                        promoteResultText = null
                    },
                    onOpenStart = onOpenStart,
                )
            }
            promoteResultText?.let { message ->
                item(key = "promoteResult") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "actions") {
                CgmCard {
                    CgmCardHeader(stringResource(R.string.cgm_actions_heading))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                    ) {
                        OutlinedButton(onClick = onOpenStart, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.dexcom_oneplus_start_action))
                        }
                        OutlinedButton(onClick = onOpenWarmup, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.dexcom_oneplus_step_warmup))
                        }
                    }
                    OutlinedButton(onClick = onOpenLog, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cgm_driver_log_open))
                    }
                }
            }
        }
    }

    if (showPromoteConfirm) {
        AlertDialog(
            onDismissRequest = { showPromoteConfirm = false },
            title = {
                Text(
                    if (promoteEarly) stringResource(R.string.dexcom_oneplus_staging_promote_early_confirm_title)
                    else stringResource(R.string.dexcom_oneplus_staging_promote_confirm_title),
                )
            },
            text = {
                Text(
                    if (promoteEarly) stringResource(R.string.dexcom_oneplus_staging_promote_early_confirm_message)
                    else stringResource(R.string.dexcom_oneplus_staging_promote_confirm_message),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPromoteConfirm = false
                        val allowEarly = promoteEarly
                        scope.launch {
                            promoteResultText = dexcomOnePlusPromotionMessage(
                                result = onPromote(allowEarly),
                                ok = promoteOk,
                                boundFailed = promoteBoundFailed,
                                followUpIdentityPresent = promoteFollowUpFailed,
                                followUpIdentityMissing = promoteFollowUpNoIdentity,
                                rejectedAbsent = promoteRejectedAbsent,
                                rejectedNotSettled = promoteRejectedNotSettled,
                                rejectedNoGlucose = promoteRejectedNoGlucose,
                                rejectedNoRecentGlucose = promoteRejectedNoRecentGlucose,
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.dexcom_oneplus_staging_promote_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPromoteConfirm = false }) {
                    Text(stringResource(R.string.dexcom_oneplus_staging_promote_dismiss))
                }
            },
        )
    }
}

/**
 * The sensor that feeds the loop: [DexcomOnePlusProductionCard], with the "correct insertion date"
 * action under it.
 */
@Composable
private fun ProductionCard(
    phaseLabel: String,
    phaseState: CgmUiState,
    sessionUp: Boolean,
    message: String,
    lifecycle: CgmSensorLifecycle?,
    newestGlucose: GV?,
    formatGlucose: (Double) -> String,
    formatTime: (Long) -> String,
    formatAge: (Long) -> String,
    correctSensorStartEnabled: Boolean,
    onCorrectSensorStart: suspend (Long) -> DexcomOnePlusSensorStartCorrection.Verdict,
) {
    DexcomOnePlusProductionCard(
        phaseLabel = phaseLabel,
        phaseState = phaseState,
        sessionUp = sessionUp,
        message = message,
        lifecycle = lifecycle,
        newestGlucose = newestGlucose,
        formatGlucose = formatGlucose,
        formatTime = formatTime,
        formatAge = formatAge,
    ) {
        // Hidden while the setting is off. The function also refuses before any write.
        if (correctSensorStartEnabled) {
            lifecycle?.startedAtEpochMs?.let { startedAt ->
                CorrectInsertionDateAction(
                    currentStartMs = startedAt,
                    formatTime = formatTime,
                    onCorrectSensorStart = onCorrectSensorStart,
                )
            }
        }
    }
}

/**
 * "The sensor went in earlier than the app thinks."
 *
 * Shown only while [DexcomOnePlusBooleanKey.CorrectSensorStart] is on.
 */
@Composable
private fun CorrectInsertionDateAction(
    currentStartMs: Long,
    formatTime: (Long) -> String,
    onCorrectSensorStart: suspend (Long) -> DexcomOnePlusSensorStartCorrection.Verdict,
) {
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    var pickedDateMs by remember { mutableStateOf<Long?>(null) }
    var refusal by remember { mutableStateOf<DexcomOnePlusSensorStartCorrection.Verdict?>(null) }

    OutlinedButton(onClick = { showDatePicker = true }) {
        Text(stringResource(R.string.dexcom_oneplus_correct_insertion_date))
    }

    if (showDatePicker) {
        DatePickerModal(
            initialDateMillis = currentStartMs,
            onDateSelected = { pickedDateMs = it },
            onDismiss = { showDatePicker = false },
        )
    }
    pickedDateMs?.let { dayMs ->
        val current = remember(currentStartMs) { Calendar.getInstance().apply { timeInMillis = currentStartMs } }
        TimePickerModal(
            initialHour = current.get(Calendar.HOUR_OF_DAY),
            initialMinute = current.get(Calendar.MINUTE),
            onTimeSelected = { hour, minute ->
                val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dayMs }
                val chosen = Calendar.getInstance().apply {
                    set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                scope.launch {
                    val verdict = onCorrectSensorStart(chosen.timeInMillis)
                    if (verdict != DexcomOnePlusSensorStartCorrection.Verdict.Accepted) refusal = verdict
                }
            },
            onDismiss = { pickedDateMs = null },
        )
    }
    refusal?.let { verdict ->
        AlertDialog(
            onDismissRequest = { refusal = null },
            title = { Text(stringResource(R.string.dexcom_oneplus_correct_insertion_date)) },
            text = {
                Text(
                    stringResource(
                        when (verdict) {
                            DexcomOnePlusSensorStartCorrection.Verdict.InFuture ->
                                R.string.dexcom_oneplus_insertion_date_future
                            DexcomOnePlusSensorStartCorrection.Verdict.TooOld ->
                                R.string.dexcom_oneplus_insertion_date_too_old
                            DexcomOnePlusSensorStartCorrection.Verdict.Accepted,
                            DexcomOnePlusSensorStartCorrection.Verdict.NoSession,
                            DexcomOnePlusSensorStartCorrection.Verdict.Disabled,
                            -> R.string.dexcom_oneplus_insertion_date_no_session
                        },
                        formatTime(currentStartMs),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { refusal = null }) { Text(coreStringResource(CoreUiStrings.ok)) }
            },
        )
    }
}

/**
 * What became of a finger prick value handed to the sensor itself.
 *
 * On screen only while [DexcomOnePlusBooleanKey.SendCalibrationToSensor] is on, which is the
 * default. The texts come from [DexcomOnePlusCalibrationText].
 *
 * [OnePlusCalibrationOutcome.Unknown] is not softened: the value was written and the answer was
 * lost or unreadable, so it is neither a failure of the app nor a success.
 * [OnePlusCalibrationOutcome.NotSent] says the opposite: nothing reached the sensor, and the value
 * may be entered again.
 */
@Composable
private fun SensorCalibrationCard(
    outcome: OnePlusCalibrationOutcome?,
    waiting: Boolean,
    driverMessage: String?,
) {
    val warning = outcome is OnePlusCalibrationOutcome.Refused || outcome is OnePlusCalibrationOutcome.NotSent
    val tone = if (warning) CgmCardTone.Warning else CgmCardTone.Neutral
    CgmCard(tone = tone) {
        CgmCardHeader(stringResource(R.string.dexcom_oneplus_calibration_heading))
        sensorCalibrationRequest(driverMessage)?.let { request ->
            Text(
                text = request,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = coreStringResource(DexcomOnePlusCalibrationText.status(outcome, waiting)),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!waiting && outcome is OnePlusCalibrationOutcome.NotSent) {
            Text(
                text = coreStringResource(DexcomOnePlusCalibrationText.notSentReason(outcome.reason)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val detail = when (outcome) {
            is OnePlusCalibrationOutcome.Accepted -> outcome.detail
            is OnePlusCalibrationOutcome.Refused -> outcome.detail
            is OnePlusCalibrationOutcome.Unknown -> outcome.detail
            else -> null
        }
        detail?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = stringResource(R.string.dexcom_oneplus_calibration_detail, text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What the sensor itself is asking for, read back out of the driver status line.
 *
 * Null when the line is about something else, which it is most of the time.
 */
@Composable
private fun sensorCalibrationRequest(driverMessage: String?): String? {
    val state = OnePlusCalibrationState.entries.firstOrNull { it.name == driverMessage } ?: return null
    return when (state) {
        OnePlusCalibrationState.NeedsCalibration ->
            stringResource(R.string.dexcom_oneplus_calibration_state_needs)
        OnePlusCalibrationState.NeedsFirstCalibration ->
            stringResource(R.string.dexcom_oneplus_calibration_state_needs_first)
        OnePlusCalibrationState.NeedsSecondCalibration ->
            stringResource(R.string.dexcom_oneplus_calibration_state_needs_second)
        OnePlusCalibrationState.CalibrationSent ->
            stringResource(R.string.dexcom_oneplus_calibration_state_sent)
        else -> null
    }
}

/**
 * The collect-only second sensor.
 *
 * Promotion stays the only full width button in the card it belongs to, so it does not compete with
 * the navigation buttons that used to sit above it at the same visual weight.
 */
@Composable
private fun StagingCard(
    stagingState: StagingState,
    stagingEvidence: CgmStagingEvidence?,
    earlyPromoteOffered: Boolean,
    formatGlucose: (Double) -> String,
    formatTime: (Long) -> String,
    onPromoteClick: (Boolean) -> Unit,
    onCancelStaging: () -> Unit,
    onOpenStart: () -> Unit,
) {
    val stagingStateLabel = when (stagingState) {
        StagingState.ABSENT   -> stringResource(R.string.dexcom_oneplus_staging_state_absent)
        StagingState.WARMUP   -> stringResource(R.string.dexcom_oneplus_staging_state_warmup)
        StagingState.SETTLING -> stringResource(R.string.dexcom_oneplus_staging_state_settling)
        StagingState.READY    -> stringResource(R.string.dexcom_oneplus_staging_state_ready)
    }
    CgmCard {
        CgmCardHeader(stringResource(R.string.dexcom_oneplus_staging_heading_short)) {
            CgmStateChip(state = stagingState.toUiState(), label = stagingStateLabel)
        }
        if (stagingState == StagingState.ABSENT) {
            // An empty state that explains what the slot is for and offers the way in, instead of
            // just reporting "None".
            Text(
                text = stringResource(R.string.dexcom_oneplus_staging_none_short),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenStart) {
                Text(stringResource(R.string.dexcom_oneplus_start_presoak))
            }
        } else {
            CgmStepTimeline(
                labels = listOf(
                    stringResource(R.string.dexcom_oneplus_staging_state_warmup),
                    stringResource(R.string.dexcom_oneplus_staging_state_settling),
                    stringResource(R.string.dexcom_oneplus_staging_state_ready),
                ),
                reached = when (stagingState) {
                    StagingState.ABSENT   -> -1
                    StagingState.WARMUP   -> 0
                    StagingState.SETTLING -> 1
                    StagingState.READY    -> 2
                },
            )
            // Evidence the staging sensor is really alive: without it the user only sees a state
            // label and cannot tell "settling with data" from "settling with a dead radio".
            CgmKeyValueRow(
                label = stringResource(R.string.dexcom_oneplus_staging_readings_label),
                value = (stagingEvidence?.validCount ?: 0).toString(),
            )
            val lastValue = stagingEvidence?.lastValueMgdl
            val lastAt = stagingEvidence?.lastValueAtEpochMs
            if (lastValue != null && lastAt != null) {
                CgmKeyValueRow(
                    label = stringResource(R.string.dexcom_oneplus_staging_last_label),
                    value = stringResource(
                        R.string.dexcom_oneplus_staging_last_reading,
                        formatGlucose(lastValue),
                        formatTime(lastAt),
                    ),
                )
            } else {
                Text(
                    text = stringResource(R.string.dexcom_oneplus_staging_no_reading_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (stagingState == StagingState.READY) {
                Button(
                    onClick = { onPromoteClick(false) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.dexcom_oneplus_staging_promote))
                }
            } else if (earlyPromoteOffered) {
                // Way out when the production sensor stops before the soak ends. Deliberately an
                // outlined button with its own warning dialog: it gives up sensor quality, so it
                // must never look like the normal path.
                Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                    OutlinedButton(
                        onClick = { onPromoteClick(true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.dexcom_oneplus_staging_promote_early))
                    }
                    Text(
                        text = stringResource(R.string.dexcom_oneplus_staging_promote_early_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onCancelStaging, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.dexcom_oneplus_staging_cancel))
            }
        }
    }
}
