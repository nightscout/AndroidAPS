package app.aaps.plugins.source.compose

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.source.CgmSensorLifecycle
import app.aaps.core.interfaces.source.CgmStagingEvidence
import app.aaps.core.interfaces.source.PromotionRejectReason
import app.aaps.core.interfaces.source.PromotionResult
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.plugins.dexcomoneplus.OnePlusCalibrationOutcome
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrationState
import app.aaps.plugins.source.DexcomOnePlusCalibrationText
import app.aaps.plugins.source.DexcomOnePlusStaging
import app.aaps.plugins.source.SourceStrings

/**
 * Everything the ONE+ / G7 status screen shows, read at one moment.
 *
 * The view model fills it from the driver, the plugin and the database; [DexcomOnePlusStatusState]
 * turns it into what is drawn. Kept as plain data so the mapping is tested without a phone.
 */
data class DexcomOnePlusStatusInput(
    val phase: OnePlusWarmupState.Phase,
    val driverMessage: String?,
    val sessionUp: Boolean,
    val lifecycle: CgmSensorLifecycle?,
    /** The newest reading in the database, from any source. Only this driver's own is shown. */
    val newestGlucose: GV?,
    val calibrationOutcome: OnePlusCalibrationOutcome?,
    val calibrationWaiting: Boolean,
    /** `DexcomOnePlusBooleanKey.SendCalibrationToSensor`. */
    val sendCalibrationToSensor: Boolean,
    /** `DexcomOnePlusBooleanKey.CorrectSensorStart`. */
    val correctSensorStartEnabled: Boolean,
    val stagingState: StagingState,
    val stagingEvidence: CgmStagingEvidence?,
    val nowMs: Long,
)

/** How the plain values of [DexcomOnePlusStatusInput] are written for a person. */
class DexcomOnePlusStatusFormat(
    val rh: TextResolver,
    val glucose: (Double) -> String,
    val time: (Long) -> String,
    val age: (Long) -> String,
)

/** What the three buttons at the bottom of the status screen do. */
class DexcomOnePlusStatusActions(
    val onOpenStart: () -> Unit,
    val onOpenWarmup: () -> Unit,
    val onOpenLog: () -> Unit,
)

/** Which promote button the pre-soak card shows. */
enum class DexcomOnePlusPromoteOffer {

    /** No button: the sensor is not ready and cannot be promoted early either. */
    NONE,

    /** The normal promote button: the pre-soak is over. */
    NORMAL,

    /** The early way out, with its own warning: the sensor is proving itself right now. */
    EARLY,
}

/** The finger prick card. Shown only while values are sent to the sensor. */
data class DexcomOnePlusCalibrationCard(
    /** What the sensor itself asks for, or null. */
    val request: TextRef?,
    val outcome: TextRef,
    /** Why a value was dropped before it was written, or null when none was dropped. */
    val notSentReason: TextRef?,
    /** The raw answer of the sensor, or null. */
    val detail: String?,
    val warning: Boolean,
)

/** The pre-soak card: the second sensor slot. */
data class DexcomOnePlusStagingCard(
    val state: StagingState,
    val validCount: Int,
    val lastValueMgdl: Double?,
    val lastValueAtMs: Long?,
    val promote: DexcomOnePlusPromoteOffer,
)

/**
 * What the status screen draws.
 *
 * [overview] is the shared pump overview: the banner, the facts of the sensor that feeds the loop,
 * and the buttons. The rest are the cards that follow it, in this order: calibration, end of life,
 * pre-soak. Keeping the pre-soak in its own card is on purpose: a reading must belong to a visible
 * subject, and the promote button is the one action here that changes the loop's glucose source.
 */
data class DexcomOnePlusStatusView(
    val overview: PumpOverviewUiState,
    val calibration: DexcomOnePlusCalibrationCard?,
    val endOfLifePrompt: Boolean,
    val staging: DexcomOnePlusStagingCard,
    /** Start time the "correct insertion date" action works on, or null when the action is hidden. */
    val correctInsertionStartMs: Long?,
)

/** Turns a [DexcomOnePlusStatusInput] into a [DexcomOnePlusStatusView]. Pure: no clock, no driver. */
object DexcomOnePlusStatusState {

    fun build(
        input: DexcomOnePlusStatusInput,
        format: DexcomOnePlusStatusFormat,
        actions: DexcomOnePlusStatusActions,
    ): DexcomOnePlusStatusView {
        val rh = format.rh
        val overview = PumpOverviewUiState(
            statusBanner = StatusBanner(
                text = rh.gs(DexcomOnePlusUiLabels.phaseLabelRef(input.phase)),
                level = bannerLevel(input.phase, input.sessionUp),
            ),
            infoRows = productionRows(input, format),
            primaryActions = listOf(
                ScreenAction(label = rh.gs(SourceStrings.dexcom_oneplus_start_action), onClick = actions.onOpenStart),
                ScreenAction(label = rh.gs(SourceStrings.dexcom_oneplus_step_warmup), onClick = actions.onOpenWarmup),
            ),
            managementActions = listOf(
                ScreenAction(label = rh.gs(SourceStrings.cgm_driver_log_open), onClick = actions.onOpenLog),
            ),
        )
        return DexcomOnePlusStatusView(
            overview = overview,
            calibration = if (input.sendCalibrationToSensor) calibrationCard(input) else null,
            // Offer a pre-soak exactly when it is useful: the sensor in use is near its end and no
            // replacement is warming up yet.
            endOfLifePrompt = input.lifecycle?.endOfLife == true && input.stagingState == StagingState.ABSENT,
            staging = stagingCard(input),
            // Hidden while the setting is off. The plugin also refuses before any write.
            correctInsertionStartMs = input.lifecycle?.startedAtEpochMs?.takeIf { input.correctSensorStartEnabled },
        )
    }

    /** Green only when the sensor is ready and the link is up; red only when the driver gave up. */
    fun bannerLevel(phase: OnePlusWarmupState.Phase, sessionUp: Boolean): StatusLevel = when (phase) {
        OnePlusWarmupState.Phase.READY  -> if (sessionUp) StatusLevel.NORMAL else StatusLevel.WARNING
        OnePlusWarmupState.Phase.FAILED -> StatusLevel.CRITICAL
        OnePlusWarmupState.Phase.IDLE   -> StatusLevel.UNSPECIFIED
        OnePlusWarmupState.Phase.PAIRING,
        OnePlusWarmupState.Phase.CONNECTING,
        OnePlusWarmupState.Phase.RECONNECTING,
        OnePlusWarmupState.Phase.WARMING -> StatusLevel.WARNING
    }

    private fun productionRows(input: DexcomOnePlusStatusInput, format: DexcomOnePlusStatusFormat): List<PumpInfoRow> {
        val rh = format.rh
        // Only this driver's own readings: the newest value in the database can come from another
        // source, and labelling someone else's reading as this sensor's would be a lie.
        val own = input.newestGlucose?.takeIf { it.sourceSensor == SourceSensor.DEXCOM_ONEPLUS_NATIVE }
        val rows = mutableListOf(
            PumpInfoRow(
                label = rh.gs(SourceStrings.dexcom_oneplus_row_last_reading),
                value = if (own != null) {
                    rh.gs(SourceStrings.dexcom_oneplus_reading_at, format.glucose(own.value), format.time(own.timestamp))
                } else {
                    rh.gs(SourceStrings.dexcom_oneplus_production_no_reading)
                },
            ),
            PumpInfoRow(
                label = rh.gs(SourceStrings.cgm_session_label),
                value = rh.gs(if (input.sessionUp) SourceStrings.dexcom_oneplus_session_up else SourceStrings.dexcom_oneplus_session_down),
                level = if (input.sessionUp) StatusLevel.NORMAL else StatusLevel.WARNING,
            ),
        )
        input.lifecycle?.ageMs?.let { age ->
            rows += PumpInfoRow(
                label = rh.gs(SourceStrings.dexcom_oneplus_row_sensor_age),
                value = format.age(age),
                level = if (input.lifecycle.endOfLife) StatusLevel.WARNING else StatusLevel.UNSPECIFIED,
            )
        }
        rows += PumpInfoRow(
            label = rh.gs(SourceStrings.dexcom_oneplus_row_message),
            value = rh.gs(DexcomOnePlusUiLabels.userMessageRef(input.driverMessage)),
        )
        return rows
    }

    /**
     * The texts come from [DexcomOnePlusCalibrationText], as for the plugin.
     *
     * [OnePlusCalibrationOutcome.Unknown] is not softened: the value was written and the answer was
     * lost or unreadable, so it is neither a failure of the app nor a success.
     * [OnePlusCalibrationOutcome.NotSent] says the opposite: nothing reached the sensor, and the
     * value may be entered again.
     */
    private fun calibrationCard(input: DexcomOnePlusStatusInput): DexcomOnePlusCalibrationCard {
        val outcome = input.calibrationOutcome
        val detail = when (outcome) {
            is OnePlusCalibrationOutcome.Accepted -> outcome.detail
            is OnePlusCalibrationOutcome.Refused  -> outcome.detail
            is OnePlusCalibrationOutcome.Unknown  -> outcome.detail
            else                                  -> null
        }
        val notSentReason = (outcome as? OnePlusCalibrationOutcome.NotSent)
            ?.takeIf { !input.calibrationWaiting }
            ?.let { DexcomOnePlusCalibrationText.notSentReason(it.reason) }
        return DexcomOnePlusCalibrationCard(
            request = calibrationRequest(input.driverMessage),
            outcome = DexcomOnePlusCalibrationText.status(outcome, input.calibrationWaiting),
            notSentReason = notSentReason,
            detail = detail?.takeIf { it.isNotBlank() },
            warning = outcome is OnePlusCalibrationOutcome.Refused || outcome is OnePlusCalibrationOutcome.NotSent,
        )
    }

    /** What the sensor itself is asking for, read back out of the driver status line, or null. */
    fun calibrationRequest(driverMessage: String?): TextRef? {
        val state = OnePlusCalibrationState.entries.firstOrNull { it.name == driverMessage } ?: return null
        return when (state) {
            OnePlusCalibrationState.NeedsCalibration       -> SourceStrings.dexcom_oneplus_calibration_state_needs
            OnePlusCalibrationState.NeedsFirstCalibration  -> SourceStrings.dexcom_oneplus_calibration_state_needs_first
            OnePlusCalibrationState.NeedsSecondCalibration -> SourceStrings.dexcom_oneplus_calibration_state_needs_second
            OnePlusCalibrationState.CalibrationSent        -> SourceStrings.dexcom_oneplus_calibration_state_sent
            else                                           -> null
        }
    }

    private fun stagingCard(input: DexcomOnePlusStatusInput): DexcomOnePlusStagingCard {
        val evidence = input.stagingEvidence
        val promote = when {
            input.stagingState == StagingState.ABSENT -> DexcomOnePlusPromoteOffer.NONE
            input.stagingState == StagingState.READY  -> DexcomOnePlusPromoteOffer.NORMAL
            // The way out when the production sensor stops before the soak ends - see canPromoteEarly.
            DexcomOnePlusStaging.canPromoteEarly(
                validEgvCount = evidence?.validCount ?: 0,
                lastValueAtEpochMs = evidence?.lastValueAtEpochMs,
                nowMs = input.nowMs,
            )                                         -> DexcomOnePlusPromoteOffer.EARLY
            else                                      -> DexcomOnePlusPromoteOffer.NONE
        }
        return DexcomOnePlusStagingCard(
            state = input.stagingState,
            validCount = evidence?.validCount ?: 0,
            lastValueMgdl = evidence?.lastValueMgdl,
            lastValueAtMs = evidence?.lastValueAtEpochMs,
            promote = promote,
        )
    }

    /**
     * Text shown after Promote. Refusal means nothing was written. [PromotionResult.OkBoundFailed]
     * means the exchange is kept and the calibration bound threw. [PromotionResult.OkFollowUpFailed]
     * means a later step threw; the wording follows
     * [PromotionResult.OkFollowUpFailed.productionIdentityPresent], which the plugin read from the
     * production store.
     */
    fun promotionMessage(result: PromotionResult): TextRef = when (result) {
        PromotionResult.Ok                  -> SourceStrings.dexcom_oneplus_staging_promote_ok
        PromotionResult.OkBoundFailed       -> SourceStrings.dexcom_oneplus_staging_promote_bound_failed
        is PromotionResult.OkFollowUpFailed ->
            if (result.productionIdentityPresent) SourceStrings.dexcom_oneplus_staging_promote_follow_up_failed
            else SourceStrings.dexcom_oneplus_staging_promote_follow_up_failed_no_identity
        is PromotionResult.Rejected         -> when (result.reason) {
            PromotionRejectReason.STAGING_ABSENT            -> SourceStrings.dexcom_oneplus_staging_promote_rejected_absent
            PromotionRejectReason.STAGING_NOT_SETTLED       -> SourceStrings.dexcom_oneplus_staging_promote_rejected_not_settled
            PromotionRejectReason.STAGING_NO_VALID_GLUCOSE  -> SourceStrings.dexcom_oneplus_staging_promote_rejected_no_glucose
            PromotionRejectReason.STAGING_NO_RECENT_GLUCOSE -> SourceStrings.dexcom_oneplus_staging_promote_rejected_no_recent_glucose
        }
    }
}
