package app.aaps.e2e

import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.di.testGraphs
import app.aaps.e2e.ScreenDriver.Companion.COMMAND_TIMEOUT
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.e2e.ui.AbstractPumpUiTest
import app.aaps.e2e.ui.PumpScreens
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danar.emulator.EmulatorRfcommTransport
import app.aaps.testcategories.ShardB

/** The DanaR screens with the emulator as a DanaR v2, see [AbstractPumpUiTest]. */
// @ShardB: with the rest of the DanaR family, see ShardA for the balance.
@ShardB
class DanaRUiTest : AbstractPumpUiTest() {

    override fun pumpUnderTest(): PumpUnderTest = DanaRPumpUnderTest(ExternalOptions.EMULATE_DANA_R_V2, "DanaRv2Plugin")
    override fun screens(): PumpScreens = DanaRScreens
}

/**
 * The DanaR pairing wizard and overview: Pairing (the phone's bonded pump, its password, Pairing), Pump history with a
 * record read from the pump, User options, and Unpair with its confirm dialog. A Dana is suspended and refilled on the
 * pump itself, so the screens have no suspend and no reservoir change.
 *
 * The treatment dialogs and the user options screen are shared with the Dana RS and get their one pass in
 * `DanaRsEmulatorUiTest`. The DanaR commands behind them are tested through the queue by the DanaR scenario classes.
 */
object DanaRScreens : PumpScreens {

    override val unpairedMark = "Pairing"
    override val pairedMark = "Unpair"

    override fun pair(screen: ScreenDriver, scenario: PumpScenario) {
        val emulator = (scenario.control as EmulatorRfcommTransport).emulator
        screen.openVia(unpairedMark, expect = "Enter pump password")
        // The pump the phone has bonded, under the name the emulator advertises.
        screen.click(testGraphs.preferences.get(DanaStringNonKey.EmulatorDeviceName))
        screen.typeIntoTheField(emulator.state.password.toString())
        // The button, not the title of the wizard.
        screen.clickButton(unpairedMark)
        screen.assertVisibleContains("Password verified", COMMAND_TIMEOUT)
        screen.openVia("Done", expect = pairedMark, exact = true)
        // A record in the pump history, for the history screen to read.
        emulator.state.reviewHistoryStore.addEvent(
            RecordTypes.RECORD_TYPE_ALARM.toInt(), System.currentTimeMillis() - HISTORY_RECORD_AGE_MS, 0, OCCLUSION.code
        )
    }

    override fun visitHistory(screen: ScreenDriver): Boolean {
        screen.openVia("Pump history", expect = "Alarms")
        // The screen opens on the alarms, and Refresh reads them from the pump.
        screen.openVia("Refresh", expect = "Occlusion")
        return true
    }

    /** The user options open from the overview, and the system Back leads back to it. */
    override fun otherFlows(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia("User options", expect = "Save options to pump")
        screen.pressBack()
        if (!screen.waitForExact(pairedMark)) throw AssertionError("System Back on the user options did not lead to the pump overview")
    }

    override fun unpair(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia(pairedMark, expect = "Reset pairing information?", exact = true)
        screen.click("OK")
        screen.waitForExactOrThrow(unpairedMark, COMMAND_TIMEOUT)
    }

    private const val HISTORY_RECORD_AGE_MS = 60 * 60 * 1000L
    /** The alarm letter of an occlusion. */
    private const val OCCLUSION = 'O'
}
