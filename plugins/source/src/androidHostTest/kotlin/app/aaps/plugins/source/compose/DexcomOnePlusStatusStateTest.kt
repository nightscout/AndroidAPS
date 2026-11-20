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
        onOpenPresoak = { opened = "presoak" },
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
        stagingState: StagingState = StagingState.ABSENT,
        stagingEvidence: CgmStagingEvidence? = null,
        driverMessage: String? = null,
        sensor: DexcomOnePlusSensorFacts? = null,
        warmupEndsAtMs: Long? = null,
    ) = DexcomOnePlusStatusInput(
        phase = phase,
        driverMessage = driverMessage,
        sessionUp = sessionUp,
        lifecycle = lifecycle,
        newestGlucose = newestGlucose,
        calibrationOutcome = calibrationOutcome,
        calibrationWaiting = calibrationWaiting,
        sendCalibrationToSensor = sendCalibrationToSensor,
        stagingState = stagingState,
        stagingEvidence = stagingEvidence,
        nowMs = NOW,
        sensor = sensor,
        warmupEndsAtMs = warmupEndsAtMs,
    )

    private fun facts(
        startedAtMs: Long? = NOW - DAY,
        sessionLengthMs: Long? = FIFTEEN_DAY_SESSION,
        lastConnectAtMs: Long? = null,
    ) = DexcomOnePlusSensorFacts(
        deviceName = "DXCM1A",
        serial = "3MT1234567",
        pairingCode = "1234",
        startedAtMs = startedAtMs,
        sessionLengthMs = sessionLengthMs,
        lastConnectAtMs = lastConnectAtMs,
    )

    private fun lifecycle(endOfLife: Boolean = false) = CgmSensorLifecycle(
        startedAtEpochMs = NOW - 5 * HOUR,
        ageMs = 5 * HOUR,
        endOfLife = endOfLife,
    )

    private fun reading(source: SourceSensor, ageMs: Long = 60_000L) = GV(
        timestamp = NOW - ageMs,
        raw = null,
        value = 120.0,
        trendArrow = TrendArrow.FLAT,
        noise = null,
        sourceSensor = source,
    )

    private fun rows(input: DexcomOnePlusStatusInput): List<PumpInfoRow> =
        DexcomOnePlusStatusState.build(input, format, actions).overview.infoRows.map { it as PumpInfoRow }

    @Test
    fun bannerFollowsTheAgeOfTheLastReading() {
        val onTime = 4 * MINUTE
        val late = 12 * MINUTE
        assertEquals(StatusLevel.NORMAL, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.READY, sessionUp = true, readingAgeMs = onTime))
        // The link is closed between two readings: that is normal, not a warning.
        assertEquals(StatusLevel.NORMAL, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.RECONNECTING, sessionUp = false, readingAgeMs = onTime))
        assertEquals(StatusLevel.NORMAL, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.CONNECTING, sessionUp = false, readingAgeMs = onTime))
        assertEquals(StatusLevel.WARNING, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.RECONNECTING, sessionUp = false, readingAgeMs = late))
        assertEquals(StatusLevel.WARNING, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.READY, sessionUp = true, readingAgeMs = late))
        assertEquals(StatusLevel.WARNING, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.RECONNECTING, sessionUp = false, readingAgeMs = null))
        assertEquals(StatusLevel.NORMAL, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.READY, sessionUp = true, readingAgeMs = null))
        assertEquals(StatusLevel.WARNING, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.WARMING, sessionUp = true, readingAgeMs = onTime))
        assertEquals(StatusLevel.CRITICAL, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.FAILED, sessionUp = false, readingAgeMs = onTime))
        assertEquals(StatusLevel.UNSPECIFIED, DexcomOnePlusStatusState.bannerLevel(OnePlusWarmupState.Phase.IDLE, sessionUp = false, readingAgeMs = null))
    }

    @Test
    fun bannerShowsThePhase() {
        val view = DexcomOnePlusStatusState.build(input(phase = OnePlusWarmupState.Phase.WARMING), format, actions)
        assertEquals("dexcom_oneplus_phase_warming", view.overview.statusBanner?.text)
    }

    @Test
    fun bannerBetweenReadingsSaysWhenTheNextOneComes() {
        val view = DexcomOnePlusStatusState.build(
            input(phase = OnePlusWarmupState.Phase.RECONNECTING, sessionUp = false, newestGlucose = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE, ageMs = 2 * MINUTE + 10_000L)),
            format, actions,
        )
        assertEquals("dexcom_oneplus_banner_next_reading_in(format_mins(3))", view.overview.statusBanner?.text)
        assertEquals(StatusLevel.NORMAL, view.overview.statusBanner?.level)
    }

    @Test
    fun bannerSaysTheReadingIsDueAfterFiveMinutes() {
        val view = DexcomOnePlusStatusState.build(
            input(phase = OnePlusWarmupState.Phase.CONNECTING, sessionUp = false, newestGlucose = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE, ageMs = 6 * MINUTE)),
            format, actions,
        )
        assertEquals("dexcom_oneplus_banner_next_reading_due", view.overview.statusBanner?.text)
    }

    @Test
    fun bannerSaysHowLongNoReadingCame() {
        val view = DexcomOnePlusStatusState.build(
            input(phase = OnePlusWarmupState.Phase.RECONNECTING, sessionUp = false, newestGlucose = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE, ageMs = 14 * MINUTE)),
            format, actions,
        )
        assertEquals("dexcom_oneplus_banner_no_reading_for(format_mins(14))", view.overview.statusBanner?.text)
        assertEquals(StatusLevel.WARNING, view.overview.statusBanner?.level)
    }

    @Test
    fun ownReadingIsShownWithItsAge() {
        val first = rows(input(newestGlucose = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE))).first()
        assertEquals("dexcom_oneplus_row_last_reading", first.label)
        assertEquals("dexcom_oneplus_reading_trend_at_ago(120 mg/dl|a11y_arrow_flat|t${NOW - 60_000L}|format_mins(1))", first.value)
        assertEquals(StatusLevel.UNSPECIFIED, first.level)
    }

    @Test
    fun bannerSaysHowLongTheSensorLasts() {
        val view = DexcomOnePlusStatusState.build(
            input(newestGlucose = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE), sensor = facts()),
            format, actions,
        )
        // Started a day ago, a 15-day sensor: 14 days left.
        assertEquals("dexcom_oneplus_banner_ok_left(${14 * 24} h)", view.overview.statusBanner?.text)
        assertEquals(StatusLevel.NORMAL, view.overview.statusBanner?.level)
    }

    @Test
    fun bannerBetweenReadingsSaysTheLifeAndTheNextReading() {
        val view = DexcomOnePlusStatusState.build(
            input(
                phase = OnePlusWarmupState.Phase.RECONNECTING, sessionUp = false,
                newestGlucose = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE, ageMs = 2 * MINUTE + 10_000L),
                sensor = facts(),
            ),
            format, actions,
        )
        assertEquals("dexcom_oneplus_banner_left_next_reading_in(${14 * 24} h|format_mins(3))", view.overview.statusBanner?.text)
    }

    @Test
    fun bannerWarnsInTheGracePeriod() {
        // A 15-day sensor started 15 days and 2 hours ago: 10 hours of grace left.
        val view = DexcomOnePlusStatusState.build(
            input(newestGlucose = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE), sensor = facts(startedAtMs = NOW - 15 * DAY - 2 * HOUR)),
            format, actions,
        )
        assertEquals("dexcom_oneplus_banner_grace(10 h)", view.overview.statusBanner?.text)
        assertEquals(StatusLevel.WARNING, view.overview.statusBanner?.level)
    }

    /** A 10-day sensor can be a G7 or a ONE+, and nothing says which: it keeps the neutral name. */
    @Test
    fun aTenDaySensorIsNotCalledG7() {
        val tenDays = 10 * DAY + 12 * HOUR
        val shown = rows(input(newestGlucose = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE), sensor = facts(sessionLengthMs = tenDays)))
            .associate { it.label to it.value }
        assertEquals("dexcom_oneplus_sensor_name_days(DXCM1A|10)", shown["dexcom_oneplus_row_sensor"])
    }

    @Test
    fun sensorRowsShowNameDatesSerialAndCode() {
        val shown = rows(input(newestGlucose = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE), sensor = facts())).associate { it.label to it.value }
        // 15 days: only a G7 runs that long, so the name says G7.
        assertEquals("dexcom_oneplus_sensor_name_g7_days(DXCM1A|15)", shown["dexcom_oneplus_row_sensor"])
        assertEquals("t${NOW - DAY}", shown["dexcom_oneplus_row_started"])
        assertEquals("dexcom_oneplus_ends_in(t${NOW + 14 * DAY}|${14 * 24} h)", shown["dexcom_oneplus_row_ends"])
        assertEquals("t${NOW + 14 * DAY + 12 * HOUR}", shown["dexcom_oneplus_row_grace_ends"])
        assertEquals("3MT1234567", shown["dexcom_oneplus_row_serial"])
        // In clear on purpose: it is how the user finds the code again for a re-pairing.
        assertEquals("1234", shown["dexcom_oneplus_row_pairing_code"])
    }

    @Test
    fun aSensorThatToldNothingEndsAfterTenDays() {
        val shown = rows(input(sensor = facts(sessionLengthMs = null))).associate { it.label to it.value }
        assertEquals("DXCM1A", shown["dexcom_oneplus_row_sensor"])
        assertEquals("dexcom_oneplus_ends_in(t${NOW + 9 * DAY}|${9 * 24} h)", shown["dexcom_oneplus_row_ends"])
    }

    @Test
    fun endRowWarnsOnTheLastDay() {
        val end = rows(input(sensor = facts(startedAtMs = NOW - 14 * DAY - 2 * HOUR))).single { it.label == "dexcom_oneplus_row_ends" }
        assertEquals(StatusLevel.WARNING, end.level)
    }

    @Test
    fun warmupEndIsShownOnlyWhileWarmingUp() {
        val warming = rows(input(phase = OnePlusWarmupState.Phase.WARMING, sensor = facts(), warmupEndsAtMs = NOW + 20 * MINUTE))
        assertEquals("t${NOW + 20 * MINUTE}", warming.single { it.label == "dexcom_oneplus_row_warmup_ends" }.value)
        val ready = rows(input(sensor = facts(), warmupEndsAtMs = NOW + 20 * MINUTE))
        assertTrue(ready.none { it.label == "dexcom_oneplus_row_warmup_ends" })
    }

    @Test
    fun lastConnectionSaysHowLongAgo() {
        val justNow = rows(input(sensor = facts(lastConnectAtMs = NOW - 30_000L))).single { it.label == "dexcom_oneplus_row_last_connection" }
        assertEquals("dexcom_oneplus_just_now", justNow.value)
        val earlier = rows(input(sensor = facts(lastConnectAtMs = NOW - 4 * MINUTE))).single { it.label == "dexcom_oneplus_row_last_connection" }
        assertEquals("dexcom_oneplus_time_ago(format_mins(4))", earlier.value)
    }

    @Test
    fun sensorStateReplacesTheDriverMessage() {
        val shown = rows(input(driverMessage = "Ok"))
        val state = shown.single { it.label == "dexcom_oneplus_row_sensor_state" }
        assertEquals("dexcom_oneplus_sensor_state_ok", state.value)
        assertEquals(StatusLevel.NORMAL, state.level)
        assertTrue(shown.none { it.label == "dexcom_oneplus_row_message" })
        val failed = rows(input(driverMessage = "SensorFailed")).single { it.label == "dexcom_oneplus_row_sensor_state" }
        assertEquals(StatusLevel.CRITICAL, failed.level)
    }

    @Test
    fun lateReadingIsAWarningRow() {
        val first = rows(input(newestGlucose = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE, ageMs = 12 * MINUTE))).first()
        assertEquals(StatusLevel.WARNING, first.level)
    }

    @Test
    fun closedLinkBetweenReadingsIsNotAWarning() {
        val session = rows(input(sessionUp = false, newestGlucose = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE, ageMs = 3 * MINUTE)))
            .single { it.label == "cgm_session_label" }
        assertEquals("dexcom_oneplus_session_between_readings", session.value)
        assertEquals(StatusLevel.UNSPECIFIED, session.level)
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
    fun bluetoothOffOnThePhoneIsSaidInTheBannerAndTheRow() {
        val off = input(sessionUp = false, phase = OnePlusWarmupState.Phase.RECONNECTING).copy(bluetoothOff = true)
        val view = DexcomOnePlusStatusState.build(off, format, actions)
        assertEquals("dexcom_oneplus_bluetooth_off", view.overview.statusBanner?.text)
        assertEquals(StatusLevel.WARNING, view.overview.statusBanner?.level)
        val session = rows(off).single { it.label == "cgm_session_label" }
        assertEquals("dexcom_oneplus_session_bluetooth_off", session.value)
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
    fun noMessageRowWhenTheDriverSaidNothing() {
        assertTrue(rows(input()).none { it.label == "dexcom_oneplus_row_message" })
    }

    @Test
    fun noMessageRowForTheMessagesOfANormalRound() {
        for (routine in listOf("waiting_for_adv", "waiting_for_next_adv", "egv_loop_exit", "connect_attempt_2")) {
            assertTrue(rows(input(driverMessage = routine)).none { it.label == "dexcom_oneplus_row_message" }, routine)
        }
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
        val overview = DexcomOnePlusStatusState.build(input(phase = OnePlusWarmupState.Phase.WARMING), format, actions).overview
        assertEquals(listOf("dexcom_oneplus_start_action", "dexcom_oneplus_warmup_title"), overview.primaryActions.map { it.label })
        assertEquals(listOf("cgm_driver_log_open"), overview.managementActions.map { it.label })
        overview.primaryActions[0].onClick()
        assertEquals("start", opened)
        overview.primaryActions[1].onClick()
        assertEquals("warmup", opened)
        overview.managementActions[0].onClick()
        assertEquals("log", opened)
    }

    @Test
    fun warmupButtonOnlyWhileThereIsAWarmupToShow() {
        fun labels(input: DexcomOnePlusStatusInput) =
            DexcomOnePlusStatusState.build(input, format, actions).overview.primaryActions.map { it.label }

        val hidden = listOf("dexcom_oneplus_start_action")
        val shown = listOf("dexcom_oneplus_start_action", "dexcom_oneplus_warmup_title")
        // A running sensor, with or without a link, and a sensor that is not connected at all.
        assertEquals(hidden, labels(input(phase = OnePlusWarmupState.Phase.READY)))
        assertEquals(hidden, labels(input(phase = OnePlusWarmupState.Phase.RECONNECTING)))
        assertEquals(hidden, labels(input(phase = OnePlusWarmupState.Phase.IDLE)))
        assertEquals(shown, labels(input(phase = OnePlusWarmupState.Phase.PAIRING)))
        assertEquals(shown, labels(input(phase = OnePlusWarmupState.Phase.FAILED)))
        // A reconnect in the middle of a warm-up keeps the button.
        assertEquals(shown, labels(input(phase = OnePlusWarmupState.Phase.RECONNECTING, warmupEndsAtMs = NOW + MINUTE)))
        assertEquals(hidden, labels(input(phase = OnePlusWarmupState.Phase.RECONNECTING, warmupEndsAtMs = NOW - MINUTE)))
        // After an app restart in a warm-up the driver knows nothing yet: a sensor started a short
        // time ago keeps the button, a day-old one does not, and neither does a ready one.
        val justStarted = facts(startedAtMs = NOW - 20 * MINUTE)
        assertEquals(shown, labels(input(phase = OnePlusWarmupState.Phase.RECONNECTING, sensor = justStarted)))
        assertEquals(shown, labels(input(phase = OnePlusWarmupState.Phase.IDLE, sensor = justStarted)))
        assertEquals(hidden, labels(input(phase = OnePlusWarmupState.Phase.READY, sensor = justStarted)))
        assertEquals(hidden, labels(input(phase = OnePlusWarmupState.Phase.RECONNECTING, sensor = facts())))
        // The first reading ends it: between two readings the phase is "reconnecting", not "ready".
        val firstReading = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE, ageMs = MINUTE)
        assertEquals(hidden, labels(input(phase = OnePlusWarmupState.Phase.RECONNECTING, sensor = justStarted, newestGlucose = firstReading)))
        // A reading of the sensor before this one does not count.
        val oldReading = reading(SourceSensor.DEXCOM_ONEPLUS_NATIVE, ageMs = 40 * MINUTE)
        assertEquals(shown, labels(input(phase = OnePlusWarmupState.Phase.RECONNECTING, sensor = justStarted, newestGlucose = oldReading)))
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
            DexcomOnePlusStatusState.build(input(stagingState = state, stagingEvidence = evidence), format, actions).staging

        assertEquals(DexcomOnePlusPromoteOffer.NONE, offer(StagingState.ABSENT).promote)
        val fresh = CgmStagingEvidence(validCount = 100, lastValueMgdl = 110.0, lastValueAtEpochMs = NOW - 60_000L)
        val ready = offer(StagingState.READY, fresh)
        assertEquals(DexcomOnePlusPromoteOffer.NORMAL, ready.promote)
        assertEquals(false, ready.needsReconnect)
        assertEquals(DexcomOnePlusPromoteOffer.NONE, offer(StagingState.SETTLING).promote)
        assertEquals(DexcomOnePlusPromoteOffer.EARLY, offer(StagingState.SETTLING, fresh).promote)
        val silent = fresh.copy(lastValueAtEpochMs = NOW - 2 * HOUR)
        val settlingSilent = offer(StagingState.SETTLING, silent)
        assertEquals(DexcomOnePlusPromoteOffer.NONE, settlingSilent.promote)
        assertEquals(true, settlingSilent.needsReconnect)
        val readySilent = offer(StagingState.READY, silent)
        assertEquals(DexcomOnePlusPromoteOffer.NONE, readySilent.promote)
        assertEquals(true, readySilent.needsReconnect)
    }

    /** A second sensor only makes sense when there is a first one. */
    @Test
    fun presoakIsNotOfferedWithoutASensor() {
        assertEquals(false, DexcomOnePlusStatusState.build(input(), format, actions).stagingOffered)
        assertEquals(true, DexcomOnePlusStatusState.build(input(lifecycle = lifecycle()), format, actions).stagingOffered)
        // A pre-soak already running stays visible, so it can be followed, promoted or cancelled.
        assertEquals(true, DexcomOnePlusStatusState.build(input(stagingState = StagingState.WARMUP), format, actions).stagingOffered)
    }

    @Test
    fun scannedTextMustBeAnApplicatorCode() {
        assertTrue(acceptApplicatorScan("1234"))
        assertFalse(acceptApplicatorScan("https://example.org"))
    }

    private companion object {

        const val HOUR = 3_600_000L
        const val MINUTE = 60_000L
        const val DAY = 24 * HOUR

        /** What a G7 15-day reports: 15 days and the 12 hour grace period. */
        const val FIFTEEN_DAY_SESSION = 15 * DAY + 12 * HOUR
        const val NOW = 1_800_000_000_000L
    }
}
