package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.e2e.scenario.AbstractPumpScenarioTest
import app.aaps.e2e.scenario.PumpUnderTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The DanaR v2 driver against its emulator, through the real command queue. See [AbstractPumpScenarioTest] and
 * [AbstractDanaRScenarioTest].
 */
@RunWith(AndroidJUnit4::class)
class DanaRv2ScenarioTest : AbstractDanaRScenarioTest() {

    override fun pumpUnderTest(): PumpUnderTest = DanaRPumpUnderTest(ExternalOptions.EMULATE_DANA_R_V2, "DanaRv2Plugin")

    /**
     * The Korean driver finds a pump that is not Korean at the start of the link and switches to the DanaR driver, not to
     * the DanaR v2 one: it cannot tell the two apart there. The DanaR driver works with a DanaR v2.
     */
    override val wrongDriver = "DanaRKoreanPlugin"
    override val driverAfterTheSwitch = "DanaRPlugin"
    override val importsPumpEvents = true
    override val alarmsOnAFarClock = true

    @Ignore("How a real DanaR goes on when it stops a bolus by itself, as at an occlusion, was never seen")
    @Test
    override fun aBolusThePumpStopsCountsWhatItGave() {
    }

    @Ignore("The DanaR emulator sends no alarms: how a real DanaR reports one was not looked at")
    @Test
    override fun anOcclusionRaisesAnAlarm() {
    }

    @Ignore("The DanaR emulator sends no alarms: how a real DanaR reports one was not looked at")
    @Test
    override fun anEmptyReservoirRaisesAnAlarm() {
    }
}
