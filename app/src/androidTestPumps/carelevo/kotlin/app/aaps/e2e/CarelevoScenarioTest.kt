package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.di.ResetGraphRule
import app.aaps.e2e.scenario.PumpScenario
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The CareLevo driver against its emulator, through the real command queue. See [PumpScenario]. */
@RunWith(AndroidJUnit4::class)
class CarelevoScenarioTest {

    // RetryRule outermost: a flaky timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(ResetGraphRule())

    // In @Before, not a field initializer: a retry runs on a new graph, see `newRxHelper`.
    private lateinit var scenario: PumpScenario

    @Before
    fun setUp() {
        scenario = PumpScenario(CarelevoPumpUnderTest())
    }

    @After
    fun tearDown() {
        scenario.tearDown()
    }

    /** A patch from activation to discard, each step checked against what the emulated patch did. */
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

    /** The patch runs the bolus and its answer is lost. AAPS must count the insulin, at the latest after a status read. */
    @Test
    fun aBolusWhoseAnswerIsLostIsCounted() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("fault").that(scenario.control.loseAnswer(EmulatedCommand.BOLUS_START)).isTrue()

        scenario.bolus(BOLUS)
        scenario.awaitTrue("the patch ended the bolus", PumpScenario.BOLUS_TIMEOUT) { !scenario.control.state().bolusRunning }
        scenario.readStatus()

        assertWithMessage("the patch gave the bolus").that(scenario.control.state().boluses).hasSize(1)
        scenario.checkBolusRecords()
    }

    /** The start never reached the patch. AAPS must not count insulin that was not given. */
    @Test
    fun aBolusStartThatIsLostGivesNoRecord() {
        scenario.bringUp()
        scenario.pair()
        assertWithMessage("fault").that(scenario.control.loseCommand(EmulatedCommand.BOLUS_START)).isTrue()

        assertWithMessage("bolus").that(scenario.bolus(BOLUS).success).isFalse()
        scenario.readStatus()

        assertWithMessage("the patch gave nothing").that(scenario.control.state().boluses).isEmpty()
        scenario.checkBolusRecords()
    }

    private companion object {

        const val BOLUS = 0.5
        const val STOPPED_BOLUS = 2.0
        const val STOP_AFTER = 0.2
        const val TEMP_BASAL_RATE = 1.0
        const val TEMP_BASAL_MINUTES = 30
    }
}
