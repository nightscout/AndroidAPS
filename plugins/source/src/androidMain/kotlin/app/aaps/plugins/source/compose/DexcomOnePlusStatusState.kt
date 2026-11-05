package app.aaps.plugins.source.compose

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.source.CgmSensorLifecycle
import app.aaps.core.interfaces.source.CgmStagingEvidence
import app.aaps.core.interfaces.source.PromotionRejectReason
import app.aaps.core.interfaces.source.PromotionResult
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.core.ui.extensions.directionToDescription
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
    /** What is stored about the sensor in use, or null when the screen has not read it yet. */
    val sensor: DexcomOnePlusSensorFacts? = null,
    /** End of the warm-up the driver works with, or null when not warming up. */
    val warmupEndsAtMs: Long? = null,
)

/** What the plugin knows about the sensor in use. Plain data, read from the slot store. */
data class DexcomOnePlusSensorFacts(
    /** The Bluetooth name of the sensor, for example `DXCMxx`. */
    val deviceName: String?,
    val serial: String?,
    /** The 4-digit code from the applicator. Shown so it can be found again for a re-pairing. */
    val pairingCode: String?,
    /** Session start, or null when unknown. */
    val startedAtMs: Long?,
    /** Whole session the sensor reported (0x52), grace period included, or null while unknown. */
    val sessionLengthMs: Long?,
    /** Last time a Bluetooth session opened in this app run, or null. */
    val lastConnectAtMs: Long?,
)

/** How the plain values of [DexcomOnePlusStatusInput] are written for a person. */
class DexcomOnePlusStatusFormat(
    val rh: TextResolver,
    val glucose: (Double) -> String,
    val time: (Long) -> String,
    val age: (Long) -> String,
    val dateTime: (Long) -> String = time,
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
            statusBanner = banner(input, format),
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

    /** The sensor sends a reading about every 5 minutes, then the link closes until the next one. */
    const val READING_INTERVAL_MS = 5L * 60_000L

    /**
     * A reading older than this is late: two readings are missed. Until then a closed link is the
     * normal gap between two radio windows, not a problem.
     */
    const val READING_LATE_MS = 11L * 60_000L

    /** The end row turns orange this long before the nominal end. */
    const val ENDING_SOON_MS = 24L * 60L * 60_000L

    private const val DAY_MS = 24L * 60L * 60_000L

    /** This driver's own newest reading. The newest value in the database can come from another source. */
    private fun ownReading(input: DexcomOnePlusStatusInput): GV? =
        input.newestGlucose?.takeIf { it.sourceSensor == SourceSensor.DEXCOM_ONEPLUS_NATIVE }

    private fun readingAgeMs(input: DexcomOnePlusStatusInput): Long? =
        ownReading(input)?.let { (input.nowMs - it.timestamp).coerceAtLeast(0L) }

    private fun isLinkPhase(phase: OnePlusWarmupState.Phase): Boolean =
        phase == OnePlusWarmupState.Phase.READY ||
            phase == OnePlusWarmupState.Phase.CONNECTING ||
            phase == OnePlusWarmupState.Phase.RECONNECTING

    /**
     * The colour of the banner.
     *
     * It follows the age of the last reading, not the Bluetooth link: the link is closed most of the
     * time, between two readings, and "Reconnecting" is then the normal state. Green while readings
     * come on time, orange when two are missed, red only when the driver gave up.
     *
     * @param readingAgeMs age of this driver's newest reading, or null when there is none.
     */
    fun bannerLevel(phase: OnePlusWarmupState.Phase, sessionUp: Boolean, readingAgeMs: Long?): StatusLevel = when {
        phase == OnePlusWarmupState.Phase.FAILED                                     -> StatusLevel.CRITICAL
        phase == OnePlusWarmupState.Phase.IDLE                                       -> StatusLevel.UNSPECIFIED
        !isLinkPhase(phase)                                                          -> StatusLevel.WARNING
        readingAgeMs != null && readingAgeMs <= READING_LATE_MS                      -> StatusLevel.NORMAL
        // Connected and ready, but nothing stored yet: the first reading is on its way.
        readingAgeMs == null && phase == OnePlusWarmupState.Phase.READY && sessionUp -> StatusLevel.NORMAL
        else                                                                         -> StatusLevel.WARNING
    }

    /** End of the nominal life (10 or 15 days), before the grace period. Null when the start is unknown. */
    private fun nominalEndMs(input: DexcomOnePlusStatusInput): Long? {
        val start = input.sensor?.startedAtMs ?: return null
        return start + DexcomOnePlusStaging.sessionLengthMs(input.sensor.sessionLengthMs) - DexcomOnePlusStaging.SENSOR_GRACE_MS
    }

    /** End of the session, grace period included. Null when the start is unknown. */
    private fun sessionEndMs(input: DexcomOnePlusStatusInput): Long? {
        val start = input.sensor?.startedAtMs ?: return null
        return start + DexcomOnePlusStaging.sessionLengthMs(input.sensor.sessionLengthMs)
    }

    private fun banner(input: DexcomOnePlusStatusInput, format: DexcomOnePlusStatusFormat): StatusBanner {
        val rh = format.rh
        val age = readingAgeMs(input)
        val level = bannerLevel(input.phase, input.sessionUp, age)
        val nominalEnd = nominalEndMs(input)
        val sessionEnd = sessionEndMs(input)
        val left = nominalEnd?.let { it - input.nowMs }?.takeIf { it > 0L }?.let { format.age(it) }
        val inGrace = nominalEnd != null && sessionEnd != null && input.nowMs >= nominalEnd && input.nowMs < sessionEnd
        val text = when {
            age == null || !isLinkPhase(input.phase)       -> rh.gs(DexcomOnePlusUiLabels.phaseLabelRef(input.phase))
            age > READING_LATE_MS                         -> rh.gs(SourceStrings.dexcom_oneplus_banner_no_reading_for, minutesText(rh, age))
            // Readings come on time, but the sensor is in its last hours: that is what matters now.
            inGrace                                       -> rh.gs(SourceStrings.dexcom_oneplus_banner_grace, format.age(sessionEnd - input.nowMs))
            // Connected right now: the reading is coming.
            input.sessionUp                               ->
                if (left != null) rh.gs(SourceStrings.dexcom_oneplus_banner_ok_left, left)
                else rh.gs(DexcomOnePlusUiLabels.phaseLabelRef(input.phase))
            age < READING_INTERVAL_MS                     -> {
                val next = minutesText(rh, READING_INTERVAL_MS - age, roundUp = true)
                if (left != null) rh.gs(SourceStrings.dexcom_oneplus_banner_left_next_reading_in, left, next)
                else rh.gs(SourceStrings.dexcom_oneplus_banner_next_reading_in, next)
            }
            left != null                                  -> rh.gs(SourceStrings.dexcom_oneplus_banner_left_next_reading_due, left)
            else                                          -> rh.gs(SourceStrings.dexcom_oneplus_banner_next_reading_due)
        }
        return StatusBanner(text = text, level = if (inGrace && level == StatusLevel.NORMAL) StatusLevel.WARNING else level)
    }

    /** "3 min ago", or "Just now" under a minute. */
    private fun agoText(rh: TextResolver, ms: Long): String =
        if (ms < 60_000L) rh.gs(SourceStrings.dexcom_oneplus_just_now)
        else rh.gs(SourceStrings.dexcom_oneplus_time_ago, minutesText(rh, ms))

    private fun minutesText(rh: TextResolver, ms: Long, roundUp: Boolean = false): String {
        val minutes = if (roundUp) (ms + 59_999L) / 60_000L else ms / 60_000L
        return rh.gs(CoreUiStrings.format_mins, minutes.toInt())
    }

    private fun productionRows(input: DexcomOnePlusStatusInput, format: DexcomOnePlusStatusFormat): List<PumpInfoRow> {
        val rh = format.rh
        // Only this driver's own readings: labelling someone else's reading as this sensor's would be a lie.
        val own = ownReading(input)
        val age = readingAgeMs(input)
        val onTime = age != null && age <= READING_LATE_MS
        val rows = mutableListOf(
            PumpInfoRow(
                label = rh.gs(SourceStrings.dexcom_oneplus_row_last_reading),
                value = if (own != null && age != null) {
                    if (own.trendArrow == TrendArrow.NONE) {
                        rh.gs(
                            SourceStrings.dexcom_oneplus_reading_at_ago,
                            format.glucose(own.value),
                            format.time(own.timestamp),
                            minutesText(rh, age),
                        )
                    } else {
                        rh.gs(
                            SourceStrings.dexcom_oneplus_reading_trend_at_ago,
                            format.glucose(own.value),
                            rh.gs(own.trendArrow.directionToDescription()),
                            format.time(own.timestamp),
                            minutesText(rh, age),
                        )
                    }
                } else {
                    rh.gs(SourceStrings.dexcom_oneplus_production_no_reading)
                },
                level = if (own != null && !onTime) StatusLevel.WARNING else StatusLevel.UNSPECIFIED,
            ),
            PumpInfoRow(
                label = rh.gs(SourceStrings.cgm_session_label),
                value = rh.gs(
                    when {
                        input.sessionUp -> SourceStrings.dexcom_oneplus_session_up
                        onTime          -> SourceStrings.dexcom_oneplus_session_between_readings
                        else            -> SourceStrings.dexcom_oneplus_session_down
                    }
                ),
                level = when {
                    input.sessionUp -> StatusLevel.NORMAL
                    onTime          -> StatusLevel.UNSPECIFIED
                    else            -> StatusLevel.WARNING
                },
            ),
        )
        input.sensor?.lastConnectAtMs?.let { at ->
            rows += PumpInfoRow(
                label = rh.gs(SourceStrings.dexcom_oneplus_row_last_connection),
                value = agoText(rh, (input.nowMs - at).coerceAtLeast(0L)),
            )
        }
        rows += sensorRows(input, format)
        input.lifecycle?.ageMs?.let { age ->
            rows += PumpInfoRow(
                label = rh.gs(SourceStrings.dexcom_oneplus_row_sensor_age),
                value = format.age(age),
                level = if (input.lifecycle.endOfLife) StatusLevel.WARNING else StatusLevel.UNSPECIFIED,
            )
        }
        input.sensor?.serial?.takeIf { it.isNotBlank() }?.let { serial ->
            rows += PumpInfoRow(label = rh.gs(SourceStrings.dexcom_oneplus_row_serial), value = serial)
        }
        input.sensor?.pairingCode?.takeIf { it.isNotBlank() }?.let { code ->
            rows += PumpInfoRow(label = rh.gs(SourceStrings.dexcom_oneplus_row_pairing_code), value = code)
        }
        // The sensor's own state when the driver line holds one, else the last driver message.
        val sensorState = sensorStateRef(input.driverMessage)
        rows += if (sensorState != null) {
            PumpInfoRow(
                label = rh.gs(SourceStrings.dexcom_oneplus_row_sensor_state),
                value = rh.gs(sensorState),
                level = sensorStateLevel(input.driverMessage),
            )
        } else {
            PumpInfoRow(
                label = rh.gs(SourceStrings.dexcom_oneplus_row_message),
                value = rh.gs(DexcomOnePlusUiLabels.userMessageRef(input.driverMessage)),
            )
        }
        return rows
    }

    /** Name, start, warm-up end, end and grace end of the sensor in use. */
    private fun sensorRows(input: DexcomOnePlusStatusInput, format: DexcomOnePlusStatusFormat): List<PumpInfoRow> {
        val rh = format.rh
        val sensor = input.sensor ?: return emptyList()
        val rows = mutableListOf<PumpInfoRow>()
        sensor.deviceName?.takeIf { it.isNotBlank() }?.let { name ->
            val days = sensor.sessionLengthMs?.let { ((it - DexcomOnePlusStaging.SENSOR_GRACE_MS + DAY_MS / 2) / DAY_MS).toInt() }
            rows += PumpInfoRow(
                label = rh.gs(SourceStrings.dexcom_oneplus_row_sensor),
                value = if (days != null) rh.gs(SourceStrings.dexcom_oneplus_sensor_name_days, name, days) else name,
            )
        }
        sensor.startedAtMs?.let { start ->
            rows += PumpInfoRow(label = rh.gs(SourceStrings.dexcom_oneplus_row_started), value = format.dateTime(start))
        }
        if (input.phase == OnePlusWarmupState.Phase.WARMING) {
            input.warmupEndsAtMs?.let { end ->
                rows += PumpInfoRow(label = rh.gs(SourceStrings.dexcom_oneplus_row_warmup_ends), value = format.time(end))
            }
        }
        val nominalEnd = nominalEndMs(input)
        val sessionEnd = sessionEndMs(input)
        if (nominalEnd != null && sessionEnd != null) {
            val left = nominalEnd - input.nowMs
            rows += PumpInfoRow(
                label = rh.gs(SourceStrings.dexcom_oneplus_row_ends),
                value = if (left > 0L) rh.gs(SourceStrings.dexcom_oneplus_ends_in, format.dateTime(nominalEnd), format.age(left))
                else format.dateTime(nominalEnd),
                level = if (left < ENDING_SOON_MS) StatusLevel.WARNING else StatusLevel.UNSPECIFIED,
            )
            rows += PumpInfoRow(
                label = rh.gs(SourceStrings.dexcom_oneplus_row_grace_ends),
                value = format.dateTime(sessionEnd),
                level = if (left < 0L) StatusLevel.WARNING else StatusLevel.UNSPECIFIED,
            )
        }
        return rows
    }

    /** What the sensor says about itself, read back out of the driver status line, or null. */
    fun sensorStateRef(driverMessage: String?): TextRef? {
        val state = OnePlusCalibrationState.entries.firstOrNull { it.name == driverMessage } ?: return null
        return when (state) {
            OnePlusCalibrationState.Ok                     -> SourceStrings.dexcom_oneplus_sensor_state_ok
            OnePlusCalibrationState.WarmingUp,
            OnePlusCalibrationState.SensorStarted          -> SourceStrings.dexcom_oneplus_sensor_state_warming
            OnePlusCalibrationState.ExcessNoise            -> SourceStrings.dexcom_oneplus_sensor_state_noise
            OnePlusCalibrationState.SensorFailed           -> SourceStrings.dexcom_oneplus_sensor_state_failed
            OnePlusCalibrationState.Ended,
            OnePlusCalibrationState.SensorExpired          -> SourceStrings.dexcom_oneplus_sensor_state_ended
            OnePlusCalibrationState.Stopped,
            OnePlusCalibrationState.SensorStopped          -> SourceStrings.dexcom_oneplus_sensor_state_stopped
            OnePlusCalibrationState.NeedsCalibration       -> SourceStrings.dexcom_oneplus_calibration_state_needs
            OnePlusCalibrationState.NeedsFirstCalibration  -> SourceStrings.dexcom_oneplus_calibration_state_needs_first
            OnePlusCalibrationState.NeedsSecondCalibration -> SourceStrings.dexcom_oneplus_calibration_state_needs_second
            OnePlusCalibrationState.CalibrationSent        -> SourceStrings.dexcom_oneplus_calibration_state_sent
            OnePlusCalibrationState.Unknown,
            OnePlusCalibrationState.Other                  -> null
        }
    }

    private fun sensorStateLevel(driverMessage: String?): StatusLevel {
        val state = OnePlusCalibrationState.entries.firstOrNull { it.name == driverMessage } ?: return StatusLevel.UNSPECIFIED
        return when {
            state == OnePlusCalibrationState.Ok -> StatusLevel.NORMAL
            state.failed()                      -> StatusLevel.CRITICAL
            state == OnePlusCalibrationState.ExcessNoise ||
                state == OnePlusCalibrationState.NeedsCalibration ||
                state == OnePlusCalibrationState.NeedsFirstCalibration ||
                state == OnePlusCalibrationState.NeedsSecondCalibration -> StatusLevel.WARNING
            else                                -> StatusLevel.UNSPECIFIED
        }
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
