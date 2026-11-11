package app.aaps.pump.dana

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DanaPumpTest : TestBaseWithProfile() {

    private lateinit var sut: DanaPump

    @BeforeEach
    fun setup() {
        sut = DanaPump(aapsLogger, preferences, dateUtil, decimalFormatter, profileStoreProvider)
    }

    // 1.2 / 0.8 * 100 is 149.99999...: a plain cut gave 149, which the step down to 10 % made 140 % for a temp basal of
    // 150 %, and 0.88 / 0.8 gave 109, so 100 %: no temp basal in place of 110 %.
    @Test
    fun aTempBasalPercentIsCutOnTheExactValue() {
        assertThat(DanaPump.tempBasalPercent(1.2, 0.8)).isEqualTo(150)
        assertThat(DanaPump.tempBasalPercent(0.88, 0.8)).isEqualTo(110)
        assertThat(DanaPump.tempBasalPercent(1.65, 1.1)).isEqualTo(150)
        assertThat(DanaPump.tempBasalPercent(2.1, 0.7)).isEqualTo(300)
    }

    @Test
    fun aTempBasalPercentIsStillCut() {
        assertThat(DanaPump.tempBasalPercent(1.19, 0.8)).isEqualTo(148)
        assertThat(DanaPump.tempBasalPercent(0.0, 0.8)).isEqualTo(0)
        assertThat(DanaPump.tempBasalPercent(0.4, 0.8)).isEqualTo(50)
    }

    @Test
    fun detectDanaRS() {
        sut.hwModel = 0x05
        assertThat(sut.modelFriendlyName()).contains("DanaRS")
    }

    /** The next read reads all again, and the records of a read that runs now still belong to this pump. */
    @Test
    fun readAllAgainKeepsWhatIdentifiesThePump() {
        sut.serialNumber = "AAA00000AA"
        sut.hwModel = 0x05
        sut.lastSettingsRead = 1_000L
        sut.readHistoryFrom = 2_000L

        sut.readAllAgain()

        assertThat(sut.lastSettingsRead).isEqualTo(0L)
        assertThat(sut.readHistoryFrom).isEqualTo(0L)
        assertThat(sut.serialNumber).isEqualTo("AAA00000AA")
        assertThat(sut.hwModel).isEqualTo(0x05)
    }

    // ---- applyReadSteps (#946): the pump type says 0.05 U bolus and 0.01 U basal, this pump is set to 0.1 ----

    /** What the step messages of the pump do. */
    private fun readStepsFromPump() {
        sut.bolusStep = 0.1
        sut.basalStep = 0.1
    }

    private fun descriptionWithReadSteps(): PumpDescription = sut.applyReadSteps(PumpDescription().fillFor(PumpType.DANA_RS))

    @Test
    fun theStepsThePumpReportedGoIntoTheDescription() {
        readStepsFromPump()

        assertThat(descriptionWithReadSteps().bolusStep).isEqualTo(0.1)
        assertThat(descriptionWithReadSteps().basalStep).isEqualTo(0.1)
    }

    @Test
    fun beforeTheStepsAreReadThePumpTypeStepsStay() {
        // The messages with the steps did not come yet: the starting 0.1 is not a reading.
        assertThat(sut.bolusStep).isEqualTo(0.1)
        assertThat(sut.bolusStepRead).isFalse()

        assertThat(descriptionWithReadSteps().bolusStep).isEqualTo(0.05)
        assertThat(descriptionWithReadSteps().basalStep).isEqualTo(0.01)
    }

    /** A reset goes back to the starting 0.1, and that is not a reading, not even for a moment. */
    @Test
    fun afterAResetThePumpTypeStepsStay() {
        readStepsFromPump()
        sut.reset()

        assertThat(sut.bolusStep).isEqualTo(0.1)
        assertThat(sut.basalStep).isEqualTo(0.1)
        assertThat(sut.bolusStepRead).isFalse()
        assertThat(sut.basalStepRead).isFalse()
        assertThat(descriptionWithReadSteps().bolusStep).isEqualTo(0.05)
        assertThat(descriptionWithReadSteps().basalStep).isEqualTo(0.01)
    }

    /** A step of 0 cannot be right, and as a step it would floor every bolus to nothing. */
    @Test
    fun aStepOfZeroIsNotUsed() {
        readStepsFromPump()
        sut.bolusStep = 0.0
        sut.basalStep = 0.0

        assertThat(descriptionWithReadSteps().bolusStep).isEqualTo(0.05)
        assertThat(descriptionWithReadSteps().basalStep).isEqualTo(0.01)
    }
}
