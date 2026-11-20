package app.aaps.e2e

import app.aaps.e2e.ScreenDriver.Companion.COMMAND_TIMEOUT
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.e2e.ui.AbstractPumpUiTest
import app.aaps.e2e.ui.PumpScreens
import app.aaps.pump.aaruy.emulator.AaruyEmulatorBleTransport
import com.google.common.truth.Truth.assertWithMessage

/** The Aaruy screens, see [AbstractPumpUiTest]. */
class AaruyUiTest : AbstractPumpUiTest() {

    override fun pumpUnderTest(): PumpUnderTest = AaruyPumpUnderTest()
    override fun screens(): PumpScreens = AaruyScreens
}

/**
 * The Aaruy overview and its flows: pairing (BLE_SCAN → PRESS_BUTTON → COMPLETE; the emulated pump needs no button
 * press), the history with its type list, the reservoir change (REWIND → ASSEMBLE → PRIME with the droplets question →
 * CONNECT → FILL_CANNULA → DONE) and the unpair with its question. The pump has no stop button of its own: it stops only
 * inside the reservoir change.
 *
 * Several button labels are also part of a sentence on the same step ("press Rewind", "press Fill cannula"), so command
 * buttons are tapped with [ScreenDriver.clickButton].
 */
object AaruyScreens : PumpScreens {

    override val unpairedMark = "Pair"
    override val pairedMark = "Unpair"

    override fun pair(screen: ScreenDriver, scenario: PumpScenario) {
        // The scan starts by itself and shows the emulated pump.
        screen.openVia(unpairedMark, expect = AaruyEmulatorBleTransport.DEVICE_NAME)
        screen.click(AaruyEmulatorBleTransport.DEVICE_NAME)                 // pairs
        screen.waitForVisibleOrThrow("Pump paired", COMMAND_TIMEOUT)        // COMPLETE
        screen.clickButton("OK")
    }

    override fun visitHistory(screen: ScreenDriver): Boolean {
        screen.openVia("Pump history", expect = "Bolus", exact = true)      // the first type of the list
        screen.click("Bolus")                                               // opens the list
        screen.click("Daily totals")
        screen.waitForExactOrThrow("Daily totals")
        return true
    }

    /** A new reservoir: the pump stops at the rewind, and basal runs again after the cannula is filled. */
    override fun otherFlows(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia("Change reservoir", expect = "then press Rewind")    // REWIND
        screen.clickButton("Rewind")
        screen.assertVisibleContains("a reservoir filled with insulin", COMMAND_TIMEOUT)   // ASSEMBLE
        screen.openVia("Next", expect = "until droplets come out")                         // PRIME
        screen.clickButton("Prime")
        // "Next" is enabled once the pump says the prime is done; it only asks about the droplets.
        screen.openVia("Next", expect = "Did droplets come out", attempts = 3)
        screen.clickButton("OK")
        screen.assertVisibleContains("Connect the infusion set")                           // CONNECT
        screen.openVia("Next", expect = "then press Fill cannula")                         // FILL_CANNULA
        screen.clickButton("Fill cannula")
        screen.assertVisibleContains("Reservoir change finished", COMMAND_TIMEOUT)         // DONE
        screen.clickButton("OK")
        screen.waitForExactOrThrow(pairedMark, COMMAND_TIMEOUT)
        assertWithMessage("basal runs after the reservoir change").that(scenario.control.state().suspended).isFalse()
    }

    override fun unpair(screen: ScreenDriver, scenario: PumpScenario) {
        screen.clickButton(pairedMark)
        screen.assertVisibleContains("Unpair the pump")
        screen.clickButton("OK")                                            // through the queue, the pump is told
    }
}
