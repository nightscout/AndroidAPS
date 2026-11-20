package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
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

    private companion object {

        const val BOLUS = 0.5
    }
}
