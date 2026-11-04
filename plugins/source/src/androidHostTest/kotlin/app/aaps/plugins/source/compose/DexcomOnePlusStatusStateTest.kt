package app.aaps.plugins.source.compose

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.source.CgmSensorLifecycle
import app.aaps.core.interfaces.source.CgmStagingEvidence
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.plugins.dexcomoneplus.OnePlusCalibrationOutcome
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.session.OnePlusAuthRefusalPolicy
import app.aaps.plugins.source.SourceStrings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ONE+ / G7 status screen state, without a phone or a driver. */
class DexcomOnePlusStatusStateTest {

    /** Answers each text with its resource name and arguments, so a test reads which text it got. */
    private object NameResolver : TextResolver {

        override fun gs(ref: TextRef): String {
            val named = ref as TextRef.Named
            return if (named.args.isEmpty()) named.name
            else named.name + named.args.joinToString(prefix = "(", postfix = ")", separator = "|")
        }

        override fun gs(ref: TextRef, vararg args: Any?): String =
            if (args.isEmpty()) gs(ref) else gs(ref) + args.joinToString(prefix = "(", postfix = ")", separator = "|")

        override fun gsNotLocalised(ref: TextRef): String = gs(ref)
    }

    private val format = DexcomOnePlusStatusFormat(
        rh = NameResolver,
        glucose = { mgdl -> "${mgdl.toInt()} mg/dl" },
        time = { epochMs -> "t$epochMs" },
        age = { millis -> "${millis / 3_600_000L} h" },
    )

    private var opened = ""
    private val actions = DexcomOnePlusStatusActions(
        onOpenStart = { opened = "start" },
        onOpenWarmup = { opened = "warmup" },
        onOpenLog = { opened = "log" },
    )

    private fun input(
        phase: OnePlusWarmupState.Phase = OnePlusWarmupState.Phase.READY,
        sessionUp: Boolean = true,
        lifecycle: CgmSensorLifecycle? = null,
        newestGlucose: GV? = null,
        calibrationOutcome: OnePlusCalibrationOutcome? = null,
        calibrationWaiting: Boolean = false,
        sendCalibrationToSensor: Boolean = false,
        correctSensorStartEnabled: Boolean = false,
        stagingState: StagingState = StagingState.ABSENT,
        stagingEvidence: CgmStagingEvidence? = null,
        driverMessage: String? = null,
    ) = DexcomOnePlusStatusInput(
        phase = phase,
        driverMessage = driverMessage,
        sessionUp = sessionUp,
        lifecycle = lifecycle,
        newestGlucose = newestGlucose,
        calibrationOutcome = calibrationOutcome,
        calibrationWaiting = calibrationWaiting,
        sendCalibrationToSensor = sendCalibrationToSensor,
        correctSensorStartEnabled = correctSensorStartEnabled,
        stagingState = stagingState,
        stagingEvidence = stagingEvidence,
        nowMs = NOW,
    )

    private fun lifecycle(endOfLife: Boolean = false) = CgmSensorLifecycle(
        startedAtEpochMs = NOW - 5 * HOUR,
        ageMs = 5 * HOUR,
        endOfLife = endOfLife,
    )

    private fun reading(source: SourceSensor) = GV(
        timestamp = NOW - 60_000L,
        raw = null,
        value = 120.0,
        trendArrow = TrendArrow.FLAT,
        noise = null,
        sourceSensor = source,
    )

    private fun rows(input: DexcomOnePlusStatusInput): List<PumpInfoRow> =
        DexcomOnePlusStatusState.build(input, format, actions).overview.infoRows.map { it as PumpInfoRow }

    @Test
    fun bannerIsGreenOnlyWhenReadyAndConnected() {
        assertEquals(StatusLevel.NORMAL, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.READY, sessionUp = true))
        assertEquals(StatusLevel.WARNING, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.READY, sessionUp = false))
        assertEquals(StatusLevel.WARNING, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.WARMING, sessionUp = true))
        assertEquals(StatusLevel.CRITICAL, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.FAILED, sessionUp = false))
        assertEquals(StatusLevel.UNSPECIFIED, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.IDLE, sessionUp = false))
    }

    @Test
    fun bannerShowsThePhase() {
        val view = DexcomOnePlusStatusState.build(input(phase = OnePlusWarmupState.Phase.WARMING), format, actions)
        assertEquals("dexcom_oneplus_phase_warming", view.overview.statusBanner?.text)
    }

    @Test
    fun ownReadingIsShown() {
        val first = rows(input(newestGlucose = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE))).first()
        assertEquals("dexcom_oneplus_row_last_reading", first.label)
        assertEquals("dexcom_oneplus_reading_at(120 mg/dl|t${NOW - 60_000L})", first.value)
    }

    @Test
    fun readingOfAnotherSourceIsNotShownAsThisSensor() {
        val first = rows(input(newestGlucose = reading(SourceSensor.LIBRE_3))).first()
        assertEquals("dexcom_oneplus_production_no_reading", first.value)
    }

    @Test
    fun sessionDownIsAWarningRow() {
        val session = rows(input(sessionUp = false)).single { it.label == "cgm_session_label" }
        assertEquals("dexcom_oneplus_session_down", session.value)
        assertEquals(StatusLevel.WARNING, session.level)
    }

    @Test
    fun sensorAgeRowWarnsNearTheEnd() {
        assertNull(rows(input()).firstOrNull { it.label == "dexcom_oneplus_row_sensor_age" })
        val young = rows(input(lifecycle = lifecycle())).single { it.label == "dexcom_oneplus_row_sensor_age" }
        assertEquals("5 h", young.value)
        assertEquals(StatusLevel.UNSPECIFIED, young.level)
        val old = rows(input(lifecycle = lifecycle(endOfLife = true))).single { it.label == "dexcom_oneplus_row_sensor_age" }
        assertEquals(StatusLevel.WARNING, old.level)
    }

    @Test
    fun driverCodesAreNeverShownRaw() {
        val message = rows(input(driverMessage = "ONEPLUS_GATT_133")).last()
        assertEquals("dexcom_oneplus_row_message", message.label)
        assertEquals("dexcom_oneplus_error_connection", message.value)
    }

    @Test
    fun refusalLinesSayWhatToDo() {
        val stopped = rows(input(driverMessage = OnePlusAuthRefusalPolicy.MESSAGE_LOCKED)).last()
        assertEquals("dexcom_oneplus_error_refused_stopped(${OnePlusAuthRefusalPolicy.MAX_REFUSALS_IN_A_ROW})", stopped.value)
        val paused = rows(input(driverMessage = OnePlusAuthRefusalPolicy.MESSAGE_PAUSED)).last()
        assertEquals("dexcom_oneplus_msg_refused_pause", paused.value)
    }

    @Test
    fun buttonsOpenStartWarmupAndLog() {
        val overview = DexcomOnePlusStatusState.build(input(), format, actions).overview
        assertEquals(listOf("dexcom_oneplus_start_action", "dexcom_oneplus_step_warmup"), overview.primaryActions.map { it.label })
        assertEquals(listOf("cgm_driver_log_open"), overview.managementActions.map { it.label })
        overview.primaryActions[0].onClick()
        assertEquals("start", opened)
        overview.primaryActions[1].onClick()
        assertEquals("warmup", opened)
        overview.managementActions[0].onClick()
        assertEquals("log", opened)
    }

    @Test
    fun calibrationCardOnlyWhileValuesGoToTheSensor() {
        assertNull(DexcomOnePlusStatusState.build(input(), format, actions).calibration)
        val card = assertNotNull(
            DexcomOnePlusStatusState.build(
                input(sendCalibrationToSensor = true, calibrationOutcome = OnePlusCalibrationOutcome.Refused("0x01")),
                format,
                actions,
            ).calibration,
        )
        assertEquals(SourceStrings.dexcom_oneplus_calibration_refused, card.outcome)
        assertEquals("0x01", card.detail)
        assertNull(card.notSentReason)
        assertTrue(card.warning)
    }

    @Test
    fun unknownCalibrationAnswerIsNotSoftened() {
        val card = DexcomOnePlusStatusState.build(
            input(sendCalibrationToSensor = true, calibrationOutcome = OnePlusCalibrationOutcome.Unknown("  ")),
            format,
            actions,
        ).calibration
        assertEquals(SourceStrings.dexcom_oneplus_calibration_unknown, card?.outcome)
        assertNull(card?.detail)
        assertFalse(card?.warning ?: true)
    }

    @Test
    fun droppedCalibrationSaysWhyAndWarns() {
        val card = assertNotNull(
            DexcomOnePlusStatusState.build(
                input(
                    sendCalibrationToSensor = true,
                    calibrationOutcome = OnePlusCalibrationOutcome.NotSent(OnePlusCalibrationOutcome.NotSent.Reason.SENSOR_CHANGED),
                ),
                format,
                actions,
            ).calibration,
        )
        assertEquals(SourceStrings.dexcom_oneplus_calibration_dropped, card.outcome)
        assertEquals(SourceStrings.dexcom_oneplus_calibration_dropped_sensor_changed, card.notSentReason)
        assertTrue(card.warning)
    }

    @Test
    fun droppedCalibrationReasonIsHiddenWhileANewValueWaits() {
        val card = DexcomOnePlusStatusState.build(
            input(
                sendCalibrationToSensor = true,
                calibrationWaiting = true,
                calibrationOutcome = OnePlusCalibrationOutcome.NotSent(OnePlusCalibrationOutcome.NotSent.Reason.TOO_OLD),
            ),
            format,
            actions,
        ).calibration
        assertEquals(SourceStrings.dexcom_oneplus_calibration_waiting, card?.outcome)
        assertNull(card?.notSentReason)
    }

    @Test
    fun sensorRequestIsReadFromTheDriverLine() {
        assertEquals(SourceStrings.dexcom_oneplus_calibration_state_needs_first, DexcomOnePlusStatusState.calibrationRequest("NeedsFirstCalibration"))
        assertNull(DexcomOnePlusStatusState.calibrationRequest("auth_ok"))
        assertNull(DexcomOnePlusStatusState.calibrationRequest(null))
    }

    @Test
    fun endOfLifePromptOnlyWithoutAPreSoakSensor() {
        assertTrue(DexcomOnePlusStatusState.build(input(lifecycle = lifecycle(endOfLife = true)), format, actions).endOfLifePrompt)
        assertFalse(DexcomOnePlusStatusState.build(input(lifecycle = lifecycle()), format, actions).endOfLifePrompt)
        assertFalse(
            DexcomOnePlusStatusState.build(
                input(lifecycle = lifecycle(endOfLife = true), stagingState = StagingState.WARMUP),
                format,
                actions,
            ).endOfLifePrompt,
        )
    }

    @Test
    fun promoteOfferFollowsTheSlotState() {
        fun offer(state: StagingState, evidence: CgmStagingEvidence? = null) =
            DexcomOnePlusStatusState.build(input(stagingState = state, stagingEvidence = evidence), format, actions).staging.promote

        assertEquals(DexcomOnePlusPromoteOffer.NONE, offer(StagingState.ABSENT))
        assertEquals(DexcomOnePlusPromoteOffer.NORMAL, offer(StagingState.READY))
        assertEquals(DexcomOnePlusPromoteOffer.NONE, offer(StagingState.SETTLING))
        val alive = CgmStagingEvidence(validCount = 100, lastValueMgdl = 110.0, lastValueAtEpochMs = NOW - 60_000L)
        assertEquals(DexcomOnePlusPromoteOffer.EARLY, offer(StagingState.SETTLING, alive))
        val silent = alive.copy(lastValueAtEpochMs = NOW - 2 * HOUR)
        assertEquals(DexcomOnePlusPromoteOffer.NONE, offer(StagingState.SETTLING, silent))
    }

    @Test
    fun insertionDateActionOnlyWithTheSwitchAndASession() {
        assertNull(DexcomOnePlusStatusState.build(input(lifecycle = lifecycle()), format, actions).correctInsertionStartMs)
        assertNull(DexcomOnePlusStatusState.build(input(correctSensorStartEnabled = true), format, actions).correctInsertionStartMs)
        assertEquals(
            NOW - 5 * HOUR,
            DexcomOnePlusStatusState.build(input(correctSensorStartEnabled = true, lifecycle = lifecycle()), format, actions).correctInsertionStartMs,
        )
    }

    @Test
    fun scannedTextMustBeAnApplicatorCode() {
        assertTrue(acceptApplicatorScan("1234"))
        assertFalse(acceptApplicatorScan("https://example.org"))
    }

    private companion object {

        const val HOUR = 3_600_000L
        const val NOW = 1_800_000_000_000L
    }
}
