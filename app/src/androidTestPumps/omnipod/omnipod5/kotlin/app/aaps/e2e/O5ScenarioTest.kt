package app.aaps.e2e

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.di.testGraphs
import app.aaps.e2e.scenario.AbstractPumpScenarioTest
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.pump.omnipod.emulator.OmnipodEmulatorTransport
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** The Omnipod 5 driver against its emulator, through the real command queue. See [AbstractPumpScenarioTest]. */
@RunWith(AndroidJUnit4::class)
class O5ScenarioTest : AbstractPumpScenarioTest() {

    private lateinit var pump: O5PumpUnderTest

    override fun pumpUnderTest(): PumpUnderTest = O5PumpUnderTest().also { pump = it }

    @Ignore("A pod has no buttons: it gives a bolus only from the app")
    @Test
    override fun aBolusGivenOnThePumpIsImported() {
    }

    @Ignore("A pod has no buttons: it stops the insulin only from the app")
    @Test
    override fun aSuspendOnThePumpIsRecorded() {
    }

    @Ignore("A pod has no clock and no history: it reports only minutes since its activation and pulses")
    @Test
    override fun aBolusWithThePumpClockOffIsStoredAtItsTime() {
    }

    /**
     * A pairing that ends as done, but whose key never gives a session: seen on a real pod on 2026-11-16. The first
     * activation step then fails. The user runs it again, and the driver must pair the pod again and activate it. Before
     * the fix the pairing could only be cleared with "Discard Pod".
     */
    @Test
    fun aPairingWhoseKeyGivesNoSessionIsMadeAgain() {
        scenario.bringUp()
        val pod = (scenario.control as OmnipodEmulatorTransport).emulator
        assertWithMessage("fault").that(pod.loseNextPairingKey()).isTrue()
        pump.initializePodRetries = 1

        scenario.pair()
        assertWithMessage("tries of the first activation step that failed").that(pump.initializePodFailures).isEqualTo(1)

        assertWithMessage("status read").that(scenario.readStatus().success).isTrue()
        scenario.checkStatusShown()
        assertWithMessage("bolus").that(scenario.userBolus(BOLUS)).isTrue()
        scenario.checkBolusRecords()
        scenario.checkNoAlarm()
    }

    /**
     * A profile write between the two activation steps: seen on a real pod on 2026-11-16. The keep alive worker asked for
     * one during the first step, and it ran right after it. The pod got its basal program before the cannula step, and
     * AAPS showed "Data is coming from different pump". The driver must send the pod nothing then: the second step
     * programs the basal.
     */
    @Test
    fun aProfileWriteBetweenTheActivationStepsDoesNotReachThePod() {
        scenario.bringUp()
        var commandsSent = -1
        var basalBetweenTheSteps: List<Double>? = null
        pump.betweenActivationSteps = {
            scenario.awaitQueueIdle()
            val before = scenario.control.state().commands
            // As the keep alive worker does when the pump does not have the profile.
            testGraphs.rxBus.send(EventProfileChangeRequested())
            // The request is handled on another thread: give it time to reach the queue, then let the queue end.
            SystemClock.sleep(REQUEST_WAIT_MS)
            scenario.awaitQueueIdle()
            val state = scenario.control.state()
            commandsSent = state.commands - before
            basalBetweenTheSteps = state.basal
        }

        scenario.pair()

        assertWithMessage("commands the pod got between the two activation steps").that(commandsSent).isEqualTo(0)
        assertWithMessage("basal program of the pod between the two activation steps").that(basalBetweenTheSteps).isNull()
        assertWithMessage("status read").that(scenario.readStatus().success).isTrue()
        scenario.checkStatusShown()
        scenario.checkNoAlarm(NotificationId.FAILED_UPDATE_PROFILE)
        scenario.checkNoAlarm(NotificationId.WRONG_PUMP_DATA)
    }

    private companion object {

        const val BOLUS = 0.5

        /** From the request for a profile write to its command in the queue. It takes well under a second. */
        const val REQUEST_WAIT_MS = 3_000L
    }
}
