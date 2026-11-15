package app.aaps.e2e.ui

import app.aaps.e2e.ScreenDriver
import app.aaps.e2e.scenario.PumpScenario

/**
 * The screens of one pump driver, for [AbstractPumpUiTest]: which buttons and texts lead through its own flows. The
 * checks are shared and done by the test, on the emulated pump and in AAPS; an adapter only taps and waits.
 *
 * A click that starts a pump command must be done once ([ScreenDriver.click], [ScreenDriver.clickButton]), and its
 * result awaited. [ScreenDriver.openVia] taps again until the next screen shows, so only for plain moves.
 */
interface PumpScreens {

    /** A text of the pump overview that shows only while no pump is paired, such as its pairing button. */
    val unpairedMark: String

    /** A text of the pump overview that shows only while a pump is paired, such as its unpair button. */
    val pairedMark: String

    /** From the unpaired overview through the pairing wizard, until the overview shows [pairedMark]. */
    fun pair(screen: ScreenDriver, scenario: PumpScenario)

    /** From the paired overview through the unpair flow, until the overview shows [unpairedMark]. */
    fun unpair(screen: ScreenDriver, scenario: PumpScenario)

    /**
     * Opens the pump's history screen and stays there. False when the driver has no history screen. The test then
     * presses the system Back, which must lead to the pump overview, not out of the pump page.
     */
    fun visitHistory(screen: ScreenDriver): Boolean = false

    /** Stops insulin from the overview. False when the driver cannot do it from its screens. */
    fun suspend(screen: ScreenDriver): Boolean = false

    /** Starts insulin again after [suspend]. */
    fun resume(screen: ScreenDriver) {}

    /** The driver's other flows, from the paired overview back to it, as a reservoir change. */
    fun otherFlows(screen: ScreenDriver, scenario: PumpScenario) {}
}
