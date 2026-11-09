package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.e2e.scenario.AbstractPumpScenarioTest
import app.aaps.e2e.scenario.PumpUnderTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** The Dana RS driver against its emulator as a Dana-i, through the real command queue. See [AbstractPumpScenarioTest]. */
@RunWith(AndroidJUnit4::class)
class DanaRSScenarioTest : AbstractPumpScenarioTest() {

    override fun pumpUnderTest(): PumpUnderTest = DanaRSPumpUnderTest()

    @Ignore("How a real Dana goes on when it stops a bolus by itself, as at an occlusion, was never seen, and it cannot be made on a real pump")
    @Test
    override fun aBolusThePumpStopsCountsWhatItGave() {
    }
}
