package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.e2e.scenario.AbstractPumpScenarioTest
import app.aaps.e2e.scenario.PumpUnderTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** The CareLevo driver against its emulator, through the real command queue. See [AbstractPumpScenarioTest]. */
@RunWith(AndroidJUnit4::class)
class CarelevoScenarioTest : AbstractPumpScenarioTest() {

    override fun pumpUnderTest(): PumpUnderTest = CarelevoPumpUnderTest()

    @Ignore("A Carelevo patch has no buttons: it gives a bolus only from the app")
    @Test
    override fun aBolusGivenOnThePumpIsImported() {
    }

    @Ignore("A Carelevo patch has no buttons: it stops the insulin only from the app")
    @Test
    override fun aSuspendOnThePumpIsRecorded() {
    }

    @Ignore("The Carelevo emulator keeps no pump clock: it only answers the set time command")
    @Test
    override fun aBolusWithThePumpClockOffIsStoredAtItsTime() {
    }
}
