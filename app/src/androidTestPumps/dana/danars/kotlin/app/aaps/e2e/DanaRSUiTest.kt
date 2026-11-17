package app.aaps.e2e

import app.aaps.e2e.ScreenDriver.Companion.COMMAND_TIMEOUT
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.e2e.ui.AbstractPumpUiTest
import app.aaps.e2e.ui.PumpScreens

/** The Dana RS screens with the emulator as a Dana-i, see [AbstractPumpUiTest]. */
class DanaRSUiTest : AbstractPumpUiTest() {

    override fun pumpUnderTest(): PumpUnderTest = DanaRSPumpUnderTest()
    override fun screens(): PumpScreens = DanaRSScreens
}

/**
 * The Dana RS pairing wizard and overview: Pairing (BLE scan → the pump → the BLE5 pairing, which a Dana-i does with no
 * PIN), Pump history, and Unpair with its confirm dialog. A Dana is suspended and refilled on the pump itself, so the
 * screens have no suspend and no reservoir change.
 *
 * The PIN and error steps of the wizard, which a Dana-i never shows, are in `DanaRSPairWizardUiTest`. The user options
 * and the insulin dialogs are in `DanaRsEmulatorUiTest`.
 */
object DanaRSScreens : PumpScreens {

    override val unpairedMark = "Pairing"
    override val pairedMark = "Unpair"

    override fun pair(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia(unpairedMark, expect = "Select your pump")    // the wizard opens and scans
        // The emulator advertises one Dana-i, named UHH… as a real one. One tap: the row goes once the pairing starts.
        screen.click("UHH")
        screen.assertVisibleContains("Pairing successful", COMMAND_TIMEOUT)
        screen.openVia("Done", expect = pairedMark, exact = true)
    }

    override fun visitHistory(screen: ScreenDriver): Boolean {
        // The "Alarms" chip, not "Refresh": the overview has a "Refresh" of its own.
        screen.openVia("Pump history", expect = "Alarms")
        return true
    }

    override fun suspend(screen: ScreenDriver): Boolean = false

    override fun resume(screen: ScreenDriver) = Unit

    override fun otherFlows(screen: ScreenDriver, scenario: PumpScenario) = Unit

    override fun unpair(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia(pairedMark, expect = "Reset pairing information?", exact = true)
        screen.click("OK")
        screen.waitForExactOrThrow(unpairedMark, COMMAND_TIMEOUT)
    }
}
