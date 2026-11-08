package app.aaps.e2e.scenario

import androidx.test.platform.app.InstrumentationRegistry
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

        assertWithMessage("bolus").that(scenario.bolus(BOLUS).success).isTrue()
        scenario.checkBolusRecords()
        scenario.checkNoAlarm()
        scenario.checkLinkClosed()

        scenario.stoppedBolus(STOPPED_BOLUS, stopAfter = STOP_AFTER)
        scenario.checkBolusRecords()
        scenario.checkNoAlarm()

        assertWithMessage("temp basal").that(scenario.tempBasal(TEMP_BASAL_RATE, TEMP_BASAL_MINUTES).success).isTrue()
        scenario.checkTempBasal()
        assertWithMessage("cancel temp basal").that(scenario.cancelTempBasal().success).isTrue()
        scenario.checkTempBasal()

        scenario.unpair()
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
        const val TEMP_BASAL_RATE = 1.0
        const val TEMP_BASAL_MINUTES = 30
        const val KILLED_BOLUS = 2.0
        const val KILL_AFTER = 0.3

        fun killRestartPart(): Int? = InstrumentationRegistry.getArguments().getString(KILL_RESTART_ARGUMENT)?.toIntOrNull()
    }
}
