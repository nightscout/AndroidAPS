package app.aaps.e2e

import app.aaps.e2e.ScreenDriver.Companion.COMMAND_TIMEOUT
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.e2e.ui.AbstractPumpUiTest
import app.aaps.e2e.ui.PumpScreens

/** The Diaconn G8 screens, see [AbstractPumpUiTest]. */
class DiaconnUiTest : AbstractPumpUiTest() {

    override fun pumpUnderTest(): PumpUnderTest = DiaconnPumpUnderTest()
    override fun screens(): PumpScreens = DiaconnScreens
}

/**
 * The Diaconn G8 pairing wizard (a scan, the pump picked, done), its pump history, the pump settings and the unpair
 * confirmation. The pump has no stop of the insulin in AAPS, so there is no suspend flow.
 */
object DiaconnScreens : PumpScreens {

    override val unpairedMark = "Pair"
    override val pairedMark = "Unpair"

    override fun pair(screen: ScreenDriver, scenario: PumpScenario) {
        // The emulator advertises one pump, "DIACONN-" and its serial number.
        screen.openVia(unpairedMark, expect = "DIACONN-")
        screen.openVia("DIACONN-", expect = "Pairing successful!")
        // The plugin reads the new pump after the wizard; the overview then offers the unpair.
        screen.openVia("OK", expect = pairedMark, attempts = 4, exact = true)
    }

    override fun visitHistory(screen: ScreenDriver): Boolean {
        screen.openVia("Pump history", expect = "Alarms")
        return true
    }

    /** The pump settings open from the overview, and the system Back leads back to it. */
    override fun otherFlows(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia("Diaconn pump Settings", expect = "LCD on time")
        screen.pressBack()
        if (!screen.waitForExact(pairedMark)) throw AssertionError("System Back on the pump settings did not lead to the pump overview")
    }

    override fun unpair(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia(pairedMark, expect = "Reset pairing information?", exact = true)
        screen.click("OK")
        screen.waitForExactOrThrow(unpairedMark, COMMAND_TIMEOUT)
    }
}
