package app.aaps.e2e

import app.aaps.di.testGraphs
import app.aaps.e2e.ScreenDriver.Companion.COMMAND_TIMEOUT
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.e2e.ui.AbstractPumpUiTest
import app.aaps.e2e.ui.PumpScreens
import app.aaps.pump.equil.di.EquilAccessors
import com.google.common.truth.Truth.assertThat

/** The Equil screens, see [AbstractPumpUiTest]. */
class EquilUiTest : AbstractPumpUiTest() {

    override fun pumpUnderTest(): PumpUnderTest = EquilPumpUnderTest()
    override fun screens(): PumpScreens = EquilScreens
}

/**
 * The Equil wizards and overview: PAIR (ASSEMBLE → BLE scan → PASSWORD → FILL → ATTACH → AIR → CONFIRM, the genuine ~13
 * command activation chain), History with both tabs, Suspend and Resume, CHANGE_INSULIN with the same infusion set (no
 * AIR step, the branch pairing never takes) and UNPAIR (DETACH → CONFIRM → result).
 *
 * The FILL/AIR "Next" only enables after its async command settles, so [ScreenDriver.openVia] retries there.
 */
object EquilScreens : PumpScreens {

    override val unpairedMark = "Pair"
    override val pairedMark = "Unpair device"

    private val equilManager get() = (testGraphs.rootGraph as EquilAccessors).equilManager

    override fun pair(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia("Pair", expect = "Pair Equil patch pump")   // overview "Pair" → ASSEMBLE header
        screen.openVia("Next", expect = "Select your pump")        // ASSEMBLE → BLE_SCAN

        // BLE_SCAN: the emulator advertises one device row "Equil - A#####".
        screen.openVia("Equil -", expect = "Enter password for")   // tap device row (prefix) → PASSWORD

        // PASSWORD: an empty code is accepted; "Pair" runs getVersion → pair → pumpSettings, a command chain, so one click.
        screen.click("Pair")
        screen.assertVisibleContains("Please prime the reservoir", COMMAND_TIMEOUT)  // FILL

        // FILL: "Prime/Fill" runs the fill loop; the emulator's resistance completes it, then the primary relabels to "Next".
        screen.click("Prime/Fill")
        screen.waitForVisibleOrThrow("Next", COMMAND_TIMEOUT)
        screen.openVia("Next", expect = "attach the pump to the base")   // FILL → ATTACH
        screen.openVia("Next", expect = "purge the air")                 // ATTACH → AIR (equil_hint1 body)

        // AIR: "Purge air" runs the air command; once it settles the "Next" enables and runs the alarm → basal → time →
        // firmware tail into CONFIRM. Each attempt's wait outlasts the tail chain, so it never double-fires.
        screen.click("Purge air")
        screen.openVia("Next", expect = "to your body", attempts = 6)    // AIR → CONFIRM

        // CONFIRM: "Finish" runs insulin → model → final settings → saveActivation, then the wizard closes back to the
        // overview, now paired.
        screen.openVia("Finish", expect = pairedMark, attempts = 4, exact = true)
    }

    override fun visitHistory(screen: ScreenDriver): Boolean {
        screen.openVia("History", expect = "History action")   // command history tab
        screen.click("Equil history")                          // pump events tab
        screen.assertVisibleContains("Equil history")
        return true
    }

    override fun suspend(screen: ScreenDriver): Boolean {
        screen.openVia("Suspend", expect = "Resume delivery", exact = true)
        return true
    }

    override fun resume(screen: ScreenDriver) {
        screen.click("Resume delivery")
        screen.waitForExactOrThrow("Suspend", COMMAND_TIMEOUT)
    }

    /** A new reservoir in the same infusion set: no AIR step, and no cannula change recorded. */
    override fun otherFlows(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia("Change reservoir", expect = "Will you also put on a new infusion set")
        screen.click("No, only reservoir or battery")
        // "Next" runs the change command, which moves the plunger back: once.
        screen.click("Next")
        screen.assertVisibleContains("Please assemble the pump", COMMAND_TIMEOUT)  // ASSEMBLE ("Change cartridge")
        screen.openVia("Next", expect = "Please prime the reservoir")                // ASSEMBLE → FILL
        screen.click("Prime/Fill")
        screen.waitForVisibleOrThrow("Next", COMMAND_TIMEOUT)
        screen.openVia("Next", expect = "attach the pump to the base")               // FILL → ATTACH
        screen.openVia("Next", expect = "to your body")                              // ATTACH → CONFIRM, no AIR
        screen.openVia("Finish", expect = pairedMark, attempts = 4, exact = true)    // back to the paired overview
        assertThat(scenario.control.state().suspended).isFalse()
    }

    override fun unpair(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia(pairedMark, expect = "plunger to retract")    // UNPAIR_DETACH
        // "Next" runs the detach command, "Finish" the unpair: each once.
        screen.click("Next")
        screen.assertVisibleContains("relinquish control", COMMAND_TIMEOUT)  // UNPAIR_CONFIRM
        screen.click("Finish")
        screen.assertVisibleContains("Device unpaired", COMMAND_TIMEOUT)    // the result
        screen.click("Finish")
        screen.waitForExactOrThrow(unpairedMark, COMMAND_TIMEOUT)
        assertThat(equilManager.equilState?.serialNumber ?: "").isEmpty()
    }
}
