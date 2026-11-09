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
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.compose.DanaRSOverviewViewModel
import app.aaps.pump.danars.compose.DanaRSPairWizardViewModel
import app.aaps.pump.danars.compose.WizardStep
import app.aaps.pump.danars.di.DanaRSAccessors
import app.aaps.pump.danars.emulator.EmulatorBleTransport
import app.aaps.pump.danars.emulator.PumpEmulator

/**
 * The Dana RS driver for [PumpScenario], with the emulator as a Dana-i (BLE5): the emulator copies what a real Dana-i
 * did. The pump is paired as the wizard does it and forgotten as the pump screen does it, with their own ViewModels from
 * the app's factory and no screen. The screens have their own tests.
 */
class DanaRSPumpUnderTest : PumpUnderTest {

    override val emulatorOption = ExternalOptions.EMULATE_DANA_BLE5
    override val pluginName: String = DanaRSPlugin::class.java.simpleName

    /** A first guess with room, set from what a run shows. */
    override val maxCommandsPerStatusRead = 30

    private val accessors get() = testGraphs.rootGraph as DanaRSAccessors

    override fun control(): PumpEmulatorControl =
        checkNotNull(accessors.bleTransport as? EmulatorBleTransport) { "The Dana RS emulator is not active" }

    /** The status read after the pairing needs the driver's service, which Android binds a moment after the plugin start. */
    override fun awaitStarted(scenario: PumpScenario) {
        val service = DanaRSPlugin::class.java.getDeclaredField("danaRSService").apply { isAccessible = true }
        scenario.awaitTrue("the Dana RS service is bound") { service.get(accessors.danaRSPlugin) != null }
    }

    override fun pair(scenario: PumpScenario) {
        withViewModel(scenario, DanaRSPairWizardViewModel::class.java) { wizard ->
            scenario.onMain { wizard.startScan() }
            scenario.awaitTrue("the emulated pump is found") { wizard.uiState.value.devices.isNotEmpty() }
            scenario.onMain { wizard.selectDevice(wizard.uiState.value.devices.first()) }
            scenario.awaitTrue("the pump is paired (the wizard is at ${wizard.uiState.value.step})", PAIR_TIMEOUT) {
                wizard.uiState.value.step == WizardStep.COMPLETE
            }
            scenario.onMain { wizard.finishWizard() }
        }
        scenario.awaitTrue("the pump is configured") { accessors.danaRSPlugin.isConfigured() }
        scenario.awaitQueueIdle()
        scenario.awaitTrue("the pump is ready") { accessors.danaRSPlugin.isInitialized() }
    }

    override fun unpair(scenario: PumpScenario) {
        withViewModel(scenario, DanaRSOverviewViewModel::class.java) { overview -> scenario.onMain { overview.performUnpair() } }
        scenario.awaitQueueIdle()
        scenario.awaitTrue("no pump is paired") { !accessors.danaRSPlugin.isConfigured() }
    }

    override fun restoreEmulator(saved: ByteArray, emulators: PumpEmulators) {
        emulators.getOrCreate(PumpEmulator.KEY) { PumpEmulator.restore(saved) }
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

        /** The handshake of a new pump and the status read after it. */
        const val PAIR_TIMEOUT = 60_000L
    }
}
