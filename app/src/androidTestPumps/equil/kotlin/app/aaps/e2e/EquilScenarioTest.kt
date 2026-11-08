package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.e2e.scenario.AbstractPumpScenarioTest
import app.aaps.e2e.scenario.PumpUnderTest
import org.junit.runner.RunWith

/** The Equil driver against its emulator, through the real command queue. See [AbstractPumpScenarioTest]. */
@RunWith(AndroidJUnit4::class)
class EquilScenarioTest : AbstractPumpScenarioTest() {

    override fun pumpUnderTest(): PumpUnderTest = EquilPumpUnderTest()
}
