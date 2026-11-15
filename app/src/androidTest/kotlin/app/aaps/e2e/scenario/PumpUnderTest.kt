package app.aaps.e2e.scenario

import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.core.interfaces.pump.emulator.PumpEmulators

/**
 * One driver with its emulator, as [PumpScenario] sees it. What differs between pumps is here: how the pump is paired,
 * how its reservoir or patch is changed, and how it is forgotten. The rest of a scenario is the same for every pump.
 *
 * Each step goes the way the app goes, through the command queue where the app uses it. A step a pump does not have
 * fails with a message that says so, rather than being skipped in silence.
 */
interface PumpUnderTest {

    /** Selects the emulator. It is read when the app graph is built. */
    val emulatorOption: ExternalOptions

    /** The plugin's class name, as the config builder stores the active pump. */
    val pluginName: String

    /** A status read sends no more commands than this. More means the driver repeats itself, as in a write burst. */
    val maxCommandsPerStatusRead: Int

    /**
     * How much more than the pump gave AAPS may count for one bolus, in units. Never less. 0 for a driver that learns
     * the exact amount from the pump. A driver that works it out from times rounds up, and its stop takes a moment to
     * reach the pump: then this is that step plus that moment.
     */
    val bolusOvercount: Double get() = 0.0

    /** The test hooks of the emulator in the graph now. After an app restart this is a new object for the same pump. */
    fun control(): PumpEmulatorControl

    /** Returns once the started driver can take commands, as after the app start of a real phone. */
    fun awaitStarted(scenario: PumpScenario)

    /** Pairs the pump, or activates a patch, until it can deliver. */
    fun pair(scenario: PumpScenario)

    /** The pump is forgotten, and AAPS has no pump paired any more. */
    fun unpair(scenario: PumpScenario)

    /** Puts the emulator that [PumpEmulatorControl.saveState] saved in place, for the app start after a kill. */
    fun restoreEmulator(saved: ByteArray, emulators: PumpEmulators)
}
