package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.e2e.scenario.AbstractPumpScenarioTest
import app.aaps.e2e.scenario.PumpUnderTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** The Equil driver against its emulator, through the real command queue. See [AbstractPumpScenarioTest]. */
@RunWith(AndroidJUnit4::class)
class EquilScenarioTest : AbstractPumpScenarioTest() {

    override fun pumpUnderTest(): PumpUnderTest = EquilPumpUnderTest()

    @Ignore("How a real Equil goes on after a blockage during a bolus was never seen, and it cannot be made on a real pump")
    @Test
    override fun aBolusThePumpStopsCountsWhatItGave() {
    }
}
