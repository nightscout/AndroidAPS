package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.e2e.scenario.AbstractPumpScenarioTest
import app.aaps.e2e.scenario.PumpUnderTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** The Korean DanaR driver against its emulator, through the real command queue. See [AbstractPumpScenarioTest]. */
@RunWith(AndroidJUnit4::class)
class DanaRKoreanScenarioTest : AbstractPumpScenarioTest() {

    override fun pumpUnderTest(): PumpUnderTest = DanaRPumpUnderTest(ExternalOptions.EMULATE_DANA_R_KOREAN, "DanaRKoreanPlugin")

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

    @Ignore("Only the DanaR v2 driver imports the pump history")
    @Test
    override fun aBolusGivenOnThePumpIsImported() {
    }
}
