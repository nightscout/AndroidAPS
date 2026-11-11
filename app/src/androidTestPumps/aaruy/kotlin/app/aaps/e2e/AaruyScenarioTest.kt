package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.e2e.scenario.AbstractPumpScenarioTest
import app.aaps.e2e.scenario.PumpUnderTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** The Aaruy driver against its emulator, through the real command queue. See [AbstractPumpScenarioTest]. */
@RunWith(AndroidJUnit4::class)
class AaruyScenarioTest : AbstractPumpScenarioTest() {

    override fun pumpUnderTest(): PumpUnderTest = AaruyPumpUnderTest()

    @Ignore("An Aaruy patch has no display: whether its one button can give a bolus was not looked at, so the emulator gives none")
    @Test
    override fun aBolusGivenOnThePumpIsImported() {
    }

    @Ignore("An Aaruy patch has no display: whether its one button can stop the insulin was not looked at, so the emulator does not")
    @Test
    override fun aSuspendOnThePumpIsRecorded() {
    }

    @Ignore("The Aaruy emulator keeps its pump clock on the phone clock: a clock that is off is not emulated yet")
    @Test
    override fun aBolusWithThePumpClockOffIsStoredAtItsTime() {
    }
}
