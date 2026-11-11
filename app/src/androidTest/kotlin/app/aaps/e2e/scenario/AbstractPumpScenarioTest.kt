package app.aaps.e2e.scenario

import androidx.test.platform.app.InstrumentationRegistry
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.di.ResetGraphRule
import app.aaps.di.testGraphs
import app.aaps.e2e.RetryRule
import app.aaps.testcategories.ShardD
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The scenarios every pump with an emulator runs. A pump's test class gives its [PumpUnderTest] and can add scenarios
 * of its own; the checks are AAPS rules, the same for every pump (see [PumpScenario]).
 *
 * [appKilledDuringABolus] is a real app restart and needs two instrumentation runs, so a normal run skips it. See its
 * KDoc for how to run it.
 *
 * CI runs these on their own emulator, see [ShardD].
 */
@ShardD
abstract class AbstractPumpScenarioTest {

    abstract fun pumpUnderTest(): PumpUnderTest

    // RetryRule outermost: a flaky timeout self-heals on a fresh attempt; see [RetryRule]. Not for the restart, whose
    // first run must end the process: a retry would run in the same one.
    @get:Rule val rules: RuleChain =
        if (killRestartPart() == null) RuleChain.outerRule(RetryRule()).around(ResetGraphRule()) else RuleChain.outerRule(ResetGraphRule())

    // In @Before, not a field initializer: a retry runs on a new graph, see `newRxHelper`.
    protected lateinit var scenario: PumpScenario

    @Before
    fun setUp() {
        scenario = PumpScenario(pumpUnderTest())
    }

    @After
    fun tearDown() {
        scenario.tearDown()
    }

    /** A pump from pairing to unpairing, each step checked against what the emulated pump did. */
    @Test
    fun fullRun() {
        scenario.bringUp()
        scenario.pair()

        assertWithMessage("status read").that(scenario.readStatus().success).isTrue()
        scenario.checkNoBurst()
        scenario.checkStatusShown()

        assertWithMessage("bolus").that(scenario.userBolus(BOLUS)).isTrue()
        scenario.checkBolusRecords()
        scenario.checkNoAlarm()
        scenario.checkLinkClosed()

        // A stop by the user is no delivery failure: no alarm.
        assertWithMessage("bolus the user stops").that(scenario.stoppedBolus(STOPPED_BOLUS, stopAfter = STOP_AFTER)).isTrue()
        scenario.checkBolusRecords()
        scenario.checkNoAlarm()

        assertWithMessage("temp basal").that(scenario.tempBasal(TEMP_BASAL_RATE, TEMP_BASAL_MINUTES).success).isTrue()
        scenario.checkTempBasal()
        assertWithMessage("cancel temp basal").that(scenario.cancelTempBasal().success).isTrue()
        scenario.checkTempBasal()

        // The temp basal dialog sets one in percent on such a pump, for an hour: another path in some drivers. For half an
        // hour a Dana takes the short command the loop uses.
        if (scenario.canSetPercentTempBasal) {
            for (minutes in listOf(TEMP_BASAL_PERCENT_MINUTES, LOOP_MINUTES)) {
                assertWithMessage("temp basal in percent for $minutes min").that(scenario.tempBasalPercent(TEMP_BASAL_PERCENT, minutes).success).isTrue()
                scenario.checkTempBasal()
                assertWithMessage("cancel temp basal in percent").that(scenario.cancelTempBasal().success).isTrue()
                scenario.checkTempBasal()
            }
        }

        if (scenario.canGiveExtendedBolus) {
            assertWithMessage("extended bolus").that(scenario.extendedBolus(EXTENDED_BOLUS, EXTENDED_MINUTES).success).isTrue()
            scenario.checkExtendedBolus()
            assertWithMessage("cancel extended bolus").that(scenario.cancelExtendedBolus().success).isTrue()
            scenario.checkExtendedBolus()
        }
        if (scenario.canReadTdds) assertWithMessage("daily totals").that(scenario.readTdds().success).isTrue()

        // A profile switch reaches the pump: it gives the new basal, hour by hour.
        scenario.switchProfile(PROFILE_PERCENT)
        assertWithMessage("status read after the profile switch").that(scenario.readStatus().success).isTrue()
        scenario.checkStatusShown()

        scenario.unpair()
    }

    /**
     * The temp basals the loop asks for, one after the other, as it does every 5 minutes: with `enforceNew = false`, so a
     * driver may keep what already runs. A high temp, the same again, a zero temp, the same again, a high temp again, and
     * back to the base rate. In between, the dialog's temp basal at the base rate, which is no temp basal. After each step
     * AAPS and the pump must agree, and no command of AAPS may come back as "done on the pump".
     *
     * A pump that gives a high temp basal as an extended bolus (the DanaR) must end the one to start the other.
     */
    @Test
    fun theLoopsTempBasalsReachThePump() {
        scenario.bringUp()
        scenario.pair()
        val steps = listOf<Pair<String, () -> PumpEnactResult>>(
            "a high temp" to { scenario.loopTempBasal(HIGH_TEMP, LOOP_MINUTES) },
            "the same high temp again" to { scenario.loopTempBasal(HIGH_TEMP, LOOP_MINUTES) },
            "a zero temp while the high temp runs" to { scenario.loopTempBasal(ZERO_TEMP, LOOP_MINUTES) },
            "the same zero temp again" to { scenario.loopTempBasal(ZERO_TEMP, LOOP_MINUTES) },
            "the dialog at the base rate while the zero temp runs" to { scenario.tempBasal(PumpScenario.BASAL_RATE, LOOP_MINUTES) },
            "the dialog at the base rate with no temp" to { scenario.tempBasal(PumpScenario.BASAL_RATE, LOOP_MINUTES) },
            "a zero temp" to { scenario.loopTempBasal(ZERO_TEMP, LOOP_MINUTES) },
            "a high temp while the zero temp runs" to { scenario.loopTempBasal(HIGH_TEMP, LOOP_MINUTES) },
            "the dialog at the base rate while the high temp runs" to { scenario.tempBasal(PumpScenario.BASAL_RATE, LOOP_MINUTES) },
            "a high temp" to { scenario.loopTempBasal(HIGH_TEMP, LOOP_MINUTES) },
            "back to the base rate" to { scenario.loopCancelTempBasal() },
            "back to the base rate with no temp" to { scenario.loopCancelTempBasal() }
        )
        for ((step, run) in steps) {
            assertWithMessage(step).that(run().success).isTrue()
            scenario.checkTempBasal()
            if (scenario.canGiveExtendedBolus) scenario.checkExtendedBolus()
        }
        // A status read compares what the pump runs with what AAPS has.
        assertWithMessage("status read").that(scenario.readStatus().success).isTrue()
        scenario.checkTempBasal()
        scenario.checkNoAlarm(NotificationId.UNSUPPORTED_ACTION_IN_PUMP)
    }

    /** The pump runs the bolus and its answer is lost. AAPS must count the insulin, at the latest after a status read. */
    @Test
    fun aBolusWhoseAnswerIsLostIsCounted() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("fault").that(scenario.control.loseAnswer(EmulatedCommand.BOLUS_START)).isTrue()

        scenario.bolus(BOLUS)
        scenario.awaitTrue("the pump ended the bolus", PumpScenario.BOLUS_TIMEOUT) { !scenario.control.state().bolusRunning }
        scenario.readStatus()

        assertWithMessage("the pump gave the bolus").that(scenario.control.state().boluses).hasSize(1)
        scenario.checkBolusRecords()
    }

    /** The start never reached the pump. AAPS must not count insulin that was not given. */
    @Test
    fun aBolusStartThatIsLostGivesNoRecord() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("fault").that(scenario.control.loseCommand(EmulatedCommand.BOLUS_START)).isTrue()

        assertWithMessage("bolus").that(scenario.bolus(BOLUS).success).isFalse()
        scenario.readStatus()

        assertWithMessage("the pump gave nothing").that(scenario.control.state().boluses).isEmpty()
        // A driver that cannot tell at once counts the whole amount, and decides from the pump once the bolus would have ended.
        scenario.checkBolusRecords(PumpScenario.BOLUS_TIMEOUT)
    }

    /**
     * The pump stops a bolus on its own, as at an occlusion. AAPS must count what the pump gave, once, and must tell the
     * user that the bolus was not given in full: the bolus is not reported as given, and the alarm rings.
     *
     * Open, so a pump whose emulator cannot copy this from a real pump skips it with the reason: an `@Ignore` override.
     * A guessed emulator would make a wrong guess look like a driver bug.
     */
    @Test
    open fun aBolusThePumpStopsCountsWhatItGave() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("fault").that(scenario.control.stopNextBolusAt(PUMP_STOPS_AT)).isTrue()

        val wentThrough = scenario.userBolus(STOPPED_BOLUS)
        scenario.readStatus()

        val given = scenario.control.state().boluses
        assertWithMessage("the pump gave one bolus").that(given).hasSize(1)
        assertWithMessage("the pump stopped it early").that(given.single()).isLessThan(STOPPED_BOLUS)
        scenario.checkBolusRecords(PumpScenario.BOLUS_TIMEOUT)
        assertWithMessage("the bolus is reported as given").that(wentThrough).isFalse()
        scenario.checkAlarm()
    }

    /**
     * The driver stopped and started between commands, as a settings import does it. After its start it must hold the
     * same pump: a driver's start loads everything again, and its stop drops everything.
     */
    @Test
    fun aDriverReloadKeepsThePump() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("bolus").that(scenario.bolus(BOLUS).success).isTrue()
        assertWithMessage("temp basal").that(scenario.tempBasal(TEMP_BASAL_RATE, TEMP_BASAL_MINUTES).success).isTrue()

        scenario.reloadDriver()

        // Ready only after the first status read, as at an app start: a driver keeps no pump state it can read again.
        assertWithMessage("the pump is still paired").that(testGraphs.activePlugin.activePump.isConfigured()).isTrue()
        assertWithMessage("status read after the reload").that(scenario.readStatus().success).isTrue()
        assertWithMessage("the pump is ready after the status read").that(testGraphs.activePlugin.activePump.isInitialized()).isTrue()
        scenario.checkBolusRecords()
        scenario.checkTempBasal()
        assertWithMessage("bolus after the reload").that(scenario.bolus(BOLUS).success).isTrue()
        scenario.checkBolusRecords()
        scenario.checkNoBurst()
    }

    // ---- Broken communication ------------------------------------------------------------------------------------

    /**
     * The link breaks while the pump gives a bolus, and the pump gives the rest alone. AAPS must count what the pump
     * gave, once, at the latest after the bolus would have ended and a status read connected again.
     */
    @Test
    fun aLinkLostDuringABolusIsCountedOnce() {
        scenario.bringUp()
        scenario.pair()
        val answer = scenario.startBolus(STOPPED_BOLUS)
        scenario.awaitGiven(STOP_AFTER)
        assertWithMessage("fault").that(scenario.control.breakLink()).isTrue()
        scenario.await(answer)
        awaitBolusEndAndRead()

        assertWithMessage("the pump gave the bolus").that(scenario.control.state().boluses).hasSize(1)
        scenario.checkBolusRecords(PumpScenario.BOLUS_TIMEOUT)
    }

    /**
     * The pump cannot be reached. A bolus and a temp basal fail once the queue gives up waiting for the link, and AAPS
     * stores neither. Back in range, a status read works, and AAPS and the pump agree.
     */
    @Test
    fun aPumpOutOfRangeFailsCleanly() {
        scenario.bringUp()
        scenario.pair()
        scenario.checkLinkClosed()
        assertWithMessage("fault").that(scenario.control.setOutOfRange(true)).isTrue()
        scenario.shortenQueueConnectionLimit()

        // Both wait for the same link, so the queue gives up on both at once.
        val bolus = scenario.startBolus(BOLUS)
        val tempBasal = scenario.startTempBasal(TEMP_BASAL_RATE, TEMP_BASAL_MINUTES)
        assertWithMessage("bolus").that(scenario.await(bolus, QUEUE_GIVES_UP).success).isFalse()
        assertWithMessage("temp basal").that(scenario.await(tempBasal, QUEUE_GIVES_UP).success).isFalse()
        assertWithMessage("the pump gave nothing").that(scenario.control.state().boluses).isEmpty()
        scenario.checkBolusRecords()
        scenario.checkTempBasal()

        assertWithMessage("back in range").that(scenario.control.setOutOfRange(false)).isTrue()
        assertWithMessage("status read").that(scenario.readStatus().success).isTrue()
        scenario.checkBolusRecords()
        scenario.checkTempBasal()
    }

    /** The pump stops the bolus, and its answer to the stop is lost. AAPS must count what the pump gave, once. */
    @Test
    fun aStopWhoseAnswerIsLostCountsWhatThePumpGave() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("fault").that(scenario.control.loseAnswer(EmulatedCommand.BOLUS_STOP)).isTrue()
        val answer = scenario.startBolus(STOPPED_BOLUS)
        scenario.awaitGiven(STOP_AFTER)
        scenario.stopBolus()
        scenario.await(answer)
        awaitBolusEndAndRead()

        assertWithMessage("the pump gave the bolus").that(scenario.control.state().boluses).hasSize(1)
        scenario.checkBolusRecords(PumpScenario.BOLUS_TIMEOUT)
    }

    /**
     * The stop never reaches the pump, so it goes on. AAPS must count what the pump gave, not the part it expected: all
     * of it, unless the driver sends the stop again.
     */
    @Test
    fun aStopThatIsLostCountsWhatThePumpGave() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("fault").that(scenario.control.loseCommand(EmulatedCommand.BOLUS_STOP)).isTrue()
        val answer = scenario.startBolus(STOPPED_BOLUS)
        scenario.awaitGiven(STOP_AFTER)
        scenario.stopBolus()
        scenario.await(answer)
        awaitBolusEndAndRead()

        assertWithMessage("the pump gave the bolus").that(scenario.control.state().boluses).hasSize(1)
        scenario.checkBolusRecords(PumpScenario.BOLUS_TIMEOUT)
    }

    /**
     * The pump runs the temp basal, and its answer is lost. After a status read AAPS and the pump must agree: AAPS has
     * the temp basal, or the driver has ended it on the pump.
     */
    @Test
    fun aTempBasalWhoseAnswerIsLostIsKnown() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("fault").that(scenario.control.loseAnswer(EmulatedCommand.TEMP_BASAL_START)).isTrue()
        scenario.tempBasal(TEMP_BASAL_RATE, TEMP_BASAL_MINUTES)
        scenario.readStatus()
        scenario.checkTempBasal()
    }

    /** The pump ends the temp basal, and its answer is lost. After a status read AAPS and the pump must agree. */
    @Test
    fun aTempBasalCancelWhoseAnswerIsLostIsKnown() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("temp basal").that(scenario.tempBasal(TEMP_BASAL_RATE, TEMP_BASAL_MINUTES).success).isTrue()
        scenario.checkTempBasal()
        assertWithMessage("fault").that(scenario.control.loseAnswer(EmulatedCommand.TEMP_BASAL_STOP)).isTrue()
        scenario.cancelTempBasal()
        scenario.readStatus()
        scenario.checkTempBasal()
    }

    /**
     * The pump ends the temp basal, its answer is lost, and so is the answer to the status read that checks it. The
     * driver cannot know then, so AAPS may keep the temp basal. The next status read that gets an answer must correct it.
     * A driver that checks in another way (Dana RS reads its history) leaves the lost status answer to the first read
     * here, so there are two.
     */
    @Test
    fun aTempBasalCancelWhoseCheckIsLostTooIsKnownAfterAStatusRead() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("temp basal").that(scenario.tempBasal(TEMP_BASAL_RATE, TEMP_BASAL_MINUTES).success).isTrue()
        scenario.checkTempBasal()
        assertWithMessage("fault").that(scenario.control.loseAnswer(EmulatedCommand.TEMP_BASAL_STOP)).isTrue()
        assertWithMessage("fault").that(scenario.control.loseAnswer(EmulatedCommand.STATUS)).isTrue()
        scenario.cancelTempBasal()
        assertWithMessage("the pump ended the temp basal").that(scenario.control.state().tempBasal).isNull()
        scenario.readStatus()
        scenario.readStatus()
        scenario.checkTempBasal()
    }

    /** The temp basal command never reaches the pump. AAPS must not keep a temp basal the pump does not run. */
    @Test
    fun aTempBasalThatIsLostIsNotKept() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("fault").that(scenario.control.loseCommand(EmulatedCommand.TEMP_BASAL_START)).isTrue()
        scenario.tempBasal(TEMP_BASAL_RATE, TEMP_BASAL_MINUTES)
        assertWithMessage("the pump runs no temp basal").that(scenario.control.state().tempBasal).isNull()
        scenario.readStatus()
        scenario.checkTempBasal()
    }

    /**
     * The cancel never reaches the pump, so its temp basal goes on. AAPS must still have it, or the loop counts too little.
     * A driver that sends the cancel again (the DanaR does) ends it: then neither may have it.
     */
    @Test
    fun aTempBasalCancelThatIsLostKeepsTheTempBasal() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("temp basal").that(scenario.tempBasal(TEMP_BASAL_RATE, TEMP_BASAL_MINUTES).success).isTrue()
        assertWithMessage("fault").that(scenario.control.loseCommand(EmulatedCommand.TEMP_BASAL_STOP)).isTrue()
        scenario.cancelTempBasal()
        scenario.readStatus()
        scenario.checkTempBasal()
    }

    /** The pump takes the new basal and its answer is lost. AAPS and the pump must agree on the basal in the end. */
    @Test
    fun aProfileWhoseAnswerIsLostIsKnown() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("fault").that(scenario.control.loseAnswer(EmulatedCommand.BASAL_PROFILE)).isTrue()
        scenario.switchProfile(PROFILE_PERCENT)
        scenario.checkStatusShown()
    }

    /** The pump starts the extended bolus and its answer is lost. After a status read AAPS and the pump must agree. */
    @Test
    fun anExtendedBolusWhoseAnswerIsLostIsKnown() {
        scenario.bringUp()
        assumeTrue("The pump gives no extended bolus", scenario.canGiveExtendedBolus)
        scenario.pair()
        assertWithMessage("fault").that(scenario.control.loseAnswer(EmulatedCommand.EXTENDED_BOLUS_START)).isTrue()
        scenario.extendedBolus(EXTENDED_BOLUS, EXTENDED_MINUTES)
        assertWithMessage("the pump runs the extended bolus").that(scenario.control.state().extendedBolus).isNotNull()
        scenario.readStatus()
        scenario.checkExtendedBolus()
    }

    /** The pump ends the extended bolus and its answer is lost. After a status read AAPS and the pump must agree. */
    @Test
    fun anExtendedBolusCancelWhoseAnswerIsLostIsKnown() {
        scenario.bringUp()
        assumeTrue("The pump gives no extended bolus", scenario.canGiveExtendedBolus)
        scenario.pair()
        assertWithMessage("extended bolus").that(scenario.extendedBolus(EXTENDED_BOLUS, EXTENDED_MINUTES).success).isTrue()
        assertWithMessage("fault").that(scenario.control.loseAnswer(EmulatedCommand.EXTENDED_BOLUS_STOP)).isTrue()
        scenario.cancelExtendedBolus()
        assertWithMessage("the pump ended the extended bolus").that(scenario.control.state().extendedBolus).isNull()
        scenario.readStatus()
        scenario.checkExtendedBolus()
    }

    /**
     * The user stops the insulin on the pump itself, and later starts it again. AAPS must record the stop, or it counts
     * basal the pump does not give and IOB is too high.
     */
    @Test
    open fun aSuspendOnThePumpIsRecorded() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("a stop on the pump").that(scenario.control.suspendOnPump(true)).isTrue()
        scenario.readStatusOnNewLink()
        scenario.checkSuspend()
        assertWithMessage("a start on the pump").that(scenario.control.suspendOnPump(false)).isTrue()
        scenario.readStatusOnNewLink()
        scenario.checkSuspend()
    }

    /** The user gives a bolus on the pump itself. AAPS must import it from the pump, and only once. */
    @Test
    open fun aBolusGivenOnThePumpIsImported() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("a bolus on the pump").that(scenario.control.bolusOnPump(BOLUS)).isTrue()
        scenario.readStatusOnNewLink()
        scenario.checkBolusRecords()
        scenario.readStatusOnNewLink()
        scenario.checkBolusRecords()
    }

    /** The pump clock is off by minutes. A bolus must still be stored at the time it was given. */
    @Test
    open fun aBolusWithThePumpClockOffIsStoredAtItsTime() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("the pump clock moves").that(scenario.control.shiftPumpClock(PUMP_CLOCK_OFFSET_MS)).isTrue()
        // The driver can see the pump clock at its next connection, and set it.
        scenario.readStatusOnNewLink()
        val givenAt = System.currentTimeMillis()
        assertWithMessage("bolus").that(scenario.bolus(BOLUS).success).isTrue()
        scenario.checkBolusRecords(PumpScenario.BOLUS_TIMEOUT)
        scenario.checkNewestBolusNear(givenAt)
    }

    /** An occlusion stops the insulin. AAPS must raise an alarm the user hears. */
    @Test
    open fun anOcclusionRaisesAnAlarm() = pumpAlarmRaisesAnAlarm(EmulatedAlarm.OCCLUSION)

    /** An empty reservoir stops the insulin. AAPS must raise an alarm the user hears. */
    @Test
    open fun anEmptyReservoirRaisesAnAlarm() = pumpAlarmRaisesAnAlarm(EmulatedAlarm.EMPTY_RESERVOIR)

    private fun pumpAlarmRaisesAnAlarm(alarm: EmulatedAlarm) {
        scenario.bringUp()
        scenario.pair()
        val raisedAt = System.currentTimeMillis()
        assertWithMessage("the pump raises $alarm").that(scenario.control.raise(alarm)).isTrue()
        // AAPS learns of it at the latest at its next connection to the pump.
        scenario.readStatusOnNewLink()
        scenario.checkUrgentAlarmSince(raisedAt)
        // The alarm screen or its notification would stay into the next test.
        testGraphs.uiInteraction.stopAlarm("scenario end")
    }

    /** The pump ends a bolus in its own time, and a status read then brings AAPS up to date. */
    private fun awaitBolusEndAndRead() {
        scenario.awaitTrue("the pump ended the bolus", PumpScenario.BOLUS_TIMEOUT) { !scenario.control.state().bolusRunning }
        scenario.readStatus()
    }

    /**
     * A real app restart: the app is killed while the pump gives a bolus. Two instrumentation runs, so the second has a
     * new process with nothing left in memory, as after a kill:
     * ```
     * adb shell am instrument -w -e killRestart 1 -e class <test class>#appKilledDuringABolus <runner>
     * adb shell am instrument -w -e killRestart 2 -e class <test class>#appKilledDuringABolus <runner>
     * ```
     * Run 1 starts the bolus and saves what the emulated pump holds, and ends without any cleanup; its process ends with
     * the run. Run 2 starts the app on the same database and settings, with the emulated pump as it was, and checks
     * that AAPS counts the bolus once, with what the pump gave. Without the argument the test is skipped.
     */
    @Test
    fun appKilledDuringABolus() {
        val part = killRestartPart()
        assumeTrue("Needs two runs with -e $KILL_RESTART_ARGUMENT 1 and 2", part != null)
        if (part == 1) {
            scenario.bringUp(restartable = true)
            scenario.pair()
            // The real speed: the bolus must still run when the process ends, as it would at a kill.
            scenario.control.speed = EmulatorSpeed.REAL
            scenario.startBolus(KILLED_BOLUS)
            scenario.awaitGiven(KILL_AFTER)
            scenario.saveForKill()
        } else {
            scenario.resumeAfterKill()
            scenario.awaitTrue("the pump ended the bolus", PumpScenario.BOLUS_TIMEOUT) { !scenario.control.state().bolusRunning }
            assertWithMessage("status read after the restart").that(scenario.readStatus().success).isTrue()
            assertWithMessage("the pump gave the bolus").that(scenario.control.state().boluses).hasSize(1)
            scenario.checkBolusRecords()
        }
    }

    private companion object {

        const val KILL_RESTART_ARGUMENT = "killRestart"

        const val BOLUS = 0.5
        const val STOPPED_BOLUS = 2.0
        const val STOP_AFTER = 0.2
        const val PUMP_STOPS_AT = 0.5
        const val TEMP_BASAL_RATE = 1.0
        const val TEMP_BASAL_MINUTES = 30
        const val TEMP_BASAL_PERCENT = 150
        const val TEMP_BASAL_PERCENT_MINUTES = 60
        /** What the loop asks for: half an hour, 200 % of [PumpScenario.BASAL_RATE], and 0 U/h. */
        const val LOOP_MINUTES = 30
        const val HIGH_TEMP = 1.0
        const val ZERO_TEMP = 0.0
        const val PROFILE_PERCENT = 150
        /** Ten minutes: more than any record could be off by its own timing. */
        const val PUMP_CLOCK_OFFSET_MS = 600_000L
        const val EXTENDED_BOLUS = 1.0
        const val EXTENDED_MINUTES = 30
        const val KILLED_BOLUS = 2.0
        const val KILL_AFTER = 0.3

        /**
         * The test lowers the queue's link limit to `PumpScenario.SHORT_CONNECTION_LIMIT_S`. This leaves time for the
         * queue to end the command after that limit.
         */
        const val QUEUE_GIVES_UP = 90_000L

        fun killRestartPart(): Int? = InstrumentationRegistry.getArguments().getString(KILL_RESTART_ARGUMENT)?.toIntOrNull()
    }
}
