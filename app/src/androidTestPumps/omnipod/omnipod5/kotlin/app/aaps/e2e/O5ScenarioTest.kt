package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.e2e.scenario.AbstractPumpScenarioTest
import app.aaps.e2e.scenario.PumpUnderTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** The Omnipod 5 driver against its emulator, through the real command queue. See [AbstractPumpScenarioTest]. */
@RunWith(AndroidJUnit4::class)
class O5ScenarioTest : AbstractPumpScenarioTest() {

    override fun pumpUnderTest(): PumpUnderTest = O5PumpUnderTest()

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
}
