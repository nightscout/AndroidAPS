package app.aaps.e2e

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.di.testGraphs
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.pump.dana.compose.DanaOverviewViewModel
import app.aaps.pump.danar.compose.DanaRPairWizardViewModel
import app.aaps.pump.danar.compose.PairWizardStep
import app.aaps.pump.danar.di.DanaRAccessors
import app.aaps.pump.danar.emulator.DanaRPumpEmulator
import app.aaps.pump.danar.emulator.EmulatorRfcommTransport

/**
 * A DanaR of [emulatorOption] for the pump scenarios: the plain DanaR, the Korean one or the DanaR v2. The emulator
 * picks the variant from the option and turns on its plugin.
 *
 * Paired through the wizard's own ViewModel: the pump the phone has bonded, the password, Pair. That runs the same
 * `connectNewPump` and status read the wizard does.
 */
class DanaRPumpUnderTest(
    override val emulatorOption: ExternalOptions,
    override val pluginName: String
) : PumpUnderTest {

    /** A first guess with room, set from what a run shows. */
    override val maxCommandsPerStatusRead = 40

    private val accessors get() = testGraphs.rootGraph as DanaRAccessors

    private val pump: Pump
        get() = when (emulatorOption) {
            ExternalOptions.EMULATE_DANA_R        -> accessors.danaRPlugin
            ExternalOptions.EMULATE_DANA_R_KOREAN -> accessors.danaRKoreanPlugin
            else                                  -> accessors.danaRv2Plugin
        }

    override fun control(): PumpEmulatorControl =
        checkNotNull(accessors.rfcommTransport as? EmulatorRfcommTransport) { "The DanaR emulator is not active" }

    /** The status read after the pairing needs the driver's service, which Android binds a moment after the plugin start. */
    override fun awaitStarted(scenario: PumpScenario) {
        // In `AbstractDanaRPlugin`, one or two classes up from the variant's plugin.
        val service = generateSequence<Class<*>>(pump.javaClass) { it.superclass }
            .firstNotNullOf { type -> type.declaredFields.firstOrNull { it.name == "executionService" } }
            .apply { isAccessible = true }
        scenario.awaitTrue("the DanaR service is bound") { service.get(pump) != null }
    }

    override fun pair(scenario: PumpScenario) {
        val emulator = (control() as EmulatorRfcommTransport).emulator
        withViewModel(scenario, DanaRPairWizardViewModel::class.java) { wizard ->
            scenario.onMain { wizard.refreshBondedDevices() }
            scenario.awaitTrue("the emulated pump is bonded") { wizard.uiState.value.bondedDevices.isNotEmpty() }
            scenario.onMain {
                wizard.onDeviceSelected(wizard.uiState.value.bondedDevices.first())
                wizard.updatePassword(emulator.state.password.toString())
                wizard.pair()
            }
            scenario.awaitTrue("the pump is paired (the wizard is at ${wizard.uiState.value.step})", PAIR_TIMEOUT) {
                pump.isInitialized() || wizard.uiState.value.step == PairWizardStep.COMPLETE
            }
        }
        scenario.awaitQueueIdle()
        scenario.awaitTrue("the pump is ready", PAIR_TIMEOUT) { pump.isInitialized() }
    }

    override fun unpair(scenario: PumpScenario) {
        withViewModel(scenario, DanaOverviewViewModel::class.java) { overview -> scenario.onMain { overview.performUnpair() } }
        scenario.awaitQueueIdle()
        scenario.awaitTrue("no pump is paired") { !pump.isConfigured() }
    }

    override fun restoreEmulator(saved: ByteArray, emulators: PumpEmulators) {
        emulators.getOrCreate(DanaRPumpEmulator.KEY) { DanaRPumpEmulator(state = EmulatorRfcommTransport.restore(saved)) }
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

        const val PAIR_TIMEOUT = 60_000L
    }
}
