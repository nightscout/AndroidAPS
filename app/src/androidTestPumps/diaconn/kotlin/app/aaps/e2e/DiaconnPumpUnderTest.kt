package app.aaps.e2e

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.di.testGraphs
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.pump.diaconn.DiaconnG8Plugin
import app.aaps.pump.diaconn.compose.DiaconnOverviewViewModel
import app.aaps.pump.diaconn.compose.DiaconnPairStep
import app.aaps.pump.diaconn.compose.DiaconnPairWizardViewModel
import app.aaps.pump.diaconn.di.DiaconnAccessors
import app.aaps.pump.diaconn.emulator.DiaconnEmulatorBleTransport
import app.aaps.pump.diaconn.emulator.DiaconnPumpEmulator
import app.aaps.pump.diaconn.keys.DiaconnBooleanKey

/**
 * The Diaconn G8 driver for [PumpScenario]. The emulator copies what a real G8 (firmware 2.50) did. The pump is paired
 * as the wizard does it and forgotten as the pump screen does it, with their own ViewModels from the app's factory and no
 * screen. The screens have their own test, [DiaconnUiTest].
 */
class DiaconnPumpUnderTest : PumpUnderTest {

    override val emulatorOption = ExternalOptions.EMULATE_DIACONN
    override val pluginName: String = DiaconnG8Plugin::class.java.simpleName

    /** A first guess with room, set from what a run shows: eight reads, the clock, the log status and its pages. */
    override val maxCommandsPerStatusRead = 25

    private val accessors get() = testGraphs.rootGraph as DiaconnAccessors

    override fun control(): PumpEmulatorControl =
        checkNotNull(accessors.diaconnBleTransport as? DiaconnEmulatorBleTransport) { "The Diaconn emulator is not active" }

    /** The driver talks to the pump through its service, which Android binds a moment after the plugin start. */
    override fun awaitStarted(scenario: PumpScenario) {
        val service = DiaconnG8Plugin::class.java.getDeclaredField("diaconnG8Service").apply { isAccessible = true }
        scenario.awaitTrue("the Diaconn service is bound") { service.get(accessors.diaconnG8Plugin) != null }
    }

    /** As the wizard pairs: a scan, the pump picked, then the status read the plugin starts for a new pump. */
    override fun pair(scenario: PumpScenario) {
        // A test pump never goes to the Diaconn cloud. The driver also refuses it while the emulator is on.
        testGraphs.preferences.put(DiaconnBooleanKey.SendLogsToCloud, false)
        withViewModel(scenario, DiaconnPairWizardViewModel::class.java) { wizard ->
            scenario.onMain { wizard.startScan() }
            scenario.awaitTrue("the emulated pump is found") { wizard.uiState.value.devices.isNotEmpty() }
            scenario.onMain { wizard.selectDevice(wizard.uiState.value.devices.first()) }
            scenario.awaitTrue("the wizard is complete (it is at ${wizard.uiState.value.step})") { wizard.uiState.value.step == DiaconnPairStep.COMPLETE }
            scenario.onMain { wizard.finish() }
        }
        scenario.awaitTrue("the pump is configured") { accessors.diaconnG8Plugin.isConfigured() }
        // The status read of the new pump, and the profile write after it: the driver waits 30 s after a basal write.
        scenario.awaitQueueIdle(PAIR_TIMEOUT)
        scenario.awaitTrue("the pump is ready", PAIR_TIMEOUT) { accessors.diaconnG8Plugin.isInitialized() }
    }

    override fun unpair(scenario: PumpScenario) {
        withViewModel(scenario, DiaconnOverviewViewModel::class.java) { overview -> scenario.onMain { overview.performUnpair() } }
        scenario.awaitQueueIdle()
        scenario.awaitTrue("no pump is paired") { !accessors.diaconnG8Plugin.isConfigured() }
    }

    override fun restoreEmulator(saved: ByteArray, emulators: PumpEmulators) {
        emulators.getOrCreate(DiaconnPumpEmulator.KEY) { DiaconnPumpEmulator.restore(saved) }
    }

    /** A ViewModel from the app's factory, cleared after [block]. */
    private fun <T : ViewModel> withViewModel(scenario: PumpScenario, type: Class<T>, block: (T) -> Unit) {
        val viewModels = ViewModelStore()
        try {
            lateinit var viewModel: T
            scenario.onMain { viewModel = ViewModelProvider(viewModels, testGraphs.viewModelFactory).get(type) }
            block(viewModel)
        } finally {
            scenario.onMain { viewModels.clear() }
        }
    }

    private companion object {

        /** The status read of a new pump with its pump log, and a profile write with the 30 s the driver waits after it. */
        const val PAIR_TIMEOUT = 90_000L
    }
}
