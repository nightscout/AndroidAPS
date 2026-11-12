package app.aaps.e2e

import app.aaps.e2e.ScreenDriver.Companion.COMMAND_TIMEOUT
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.e2e.ui.AbstractPumpUiTest
import app.aaps.e2e.ui.PumpScreens
import app.aaps.pump.diaconn.emulator.DiaconnEmulatorBleTransport

/** The Diaconn G8 screens, see [AbstractPumpUiTest]. */
class DiaconnUiTest : AbstractPumpUiTest() {

    override fun pumpUnderTest(): PumpUnderTest = DiaconnPumpUnderTest()
    override fun screens(): PumpScreens = DiaconnScreens
}

/**
 * The Diaconn G8 pairing wizard (a scan, the pump picked, done), its pump history with a record read from the pump, a
 * pump setting saved to the pump, and the unpair confirmation. The pump has no stop of the insulin in AAPS, so there is no
 * suspend flow. The treatment dialogs are the same for every pump and get their one pass in `DanaRsEmulatorUiTest`.
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
        // A bolus given on the pump itself, for the history screen to read from the pump log.
        if (!scenario.control.bolusOnPump(HISTORY_BOLUS)) throw AssertionError("The emulator gave no bolus on the pump")
    }

    /** The bolus list of the history: Refresh reads the pump log, and the bolus given on the pump is in it. */
    override fun visitHistory(screen: ScreenDriver): Boolean {
        screen.openVia("Pump history", expect = "Alarms")
        screen.openVia("Alarms", expect = "Boluses")
        screen.openVia("Boluses", expect = "Refresh")
        screen.openVia("Refresh", expect = HISTORY_BOLUS_SHOWN)
        return true
    }

    /**
     * The pump settings: the system Back leads back to the overview, and the alarm set to vibrate is saved to the pump (a
     * setting with its confirm), after which the screen goes back to the overview by itself.
     */
    override fun otherFlows(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia("Diaconn pump Settings", expect = "LCD on time")
        screen.pressBack()
        if (!screen.waitForExact(pairedMark)) throw AssertionError("System Back on the pump settings did not lead to the pump overview")

        screen.openVia("Diaconn pump Settings", expect = "LCD on time")
        val pump = (scenario.control as DiaconnEmulatorBleTransport).pumpState
        screen.click("vibrate")
        // The first Save on the screen is the one of the alarm.
        screen.click("Save")
        scenario.awaitTrue("the pump alarm is set to vibrate", COMMAND_TIMEOUT) { pump.beepAndAlarm == VIBRATE }
        if (!screen.waitForExact(pairedMark, COMMAND_TIMEOUT)) throw AssertionError("A saved pump setting did not lead back to the pump overview")
    }

    override fun unpair(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia(pairedMark, expect = "Reset pairing information?", exact = true)
        screen.click("OK")
        screen.waitForExactOrThrow(unpairedMark, COMMAND_TIMEOUT)
    }

    private const val HISTORY_BOLUS = 0.5
    private const val HISTORY_BOLUS_SHOWN = "0.50 U"

    /** The alarm setting of the pump: 1 sound, 2 vibrate, 3 silent. */
    private const val VIBRATE = 2
}
