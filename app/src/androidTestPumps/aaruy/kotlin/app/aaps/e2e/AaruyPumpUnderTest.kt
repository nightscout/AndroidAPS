package app.aaps.e2e

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.di.testGraphs
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.pump.aaruy.AaruyPlugin
import app.aaps.pump.aaruy.comm.aaruyFrameKey
import app.aaps.pump.aaruy.compose.AaruyPairStep
import app.aaps.pump.aaruy.compose.AaruyPairWizardViewModel
import app.aaps.pump.aaruy.di.AaruyAccessors
import app.aaps.pump.aaruy.emulator.AaruyPumpEmulator
import app.aaps.pump.aaruy.services.AaruyUnpairCommand
import kotlinx.coroutines.runBlocking

/**
 * The Aaruy pump for [PumpScenario]. It is paired as the wizard does it, with the wizard's own ViewModel from the
 * app's factory and no screen: scan, pick the pump, wait for the link. The emulated pump needs no button press.
 */
class AaruyPumpUnderTest : PumpUnderTest {

    override val emulatorOption = ExternalOptions.EMULATE_AARUY
    override val pluginName: String = AaruyPlugin::class.java.simpleName

    /** A status read: status and battery, the settings, the time, the log. With room. */
    override val maxCommandsPerStatusRead = 15

    private val accessors get() = testGraphs.rootGraph as AaruyAccessors

    override fun control(): PumpEmulatorControl = checkNotNull(accessors.aaruyBle.emulatorTransport) { "The Aaruy emulator is not active" }

    /** The plugin's start binds its service, which every queue command needs. Private, so it is read as the plugin tests do. */
    override fun awaitStarted(scenario: PumpScenario) {
        val field = AaruyPlugin::class.java.getDeclaredField("aaruyService").apply { isAccessible = true }
        scenario.awaitTrue("the Aaruy service is bound") { field.get(accessors.aaruyPlugin) != null }
    }

    override fun pair(scenario: PumpScenario) {
        val viewModels = ViewModelStore()
        try {
            lateinit var wizard: AaruyPairWizardViewModel
            scenario.onMain { wizard = ViewModelProvider(viewModels, testGraphs.viewModelFactory).get(AaruyPairWizardViewModel::class.java) }
            // A profile switch is stored already, so the wizard starts at the scan.
            scenario.awaitTrue("the wizard is at the scan") { wizard.step.value == AaruyPairStep.BLE_SCAN }
            scenario.onMain { wizard.startScan() }
            scenario.awaitTrue("the emulated pump is found") { wizard.devices.value.isNotEmpty() }
            scenario.onMain { wizard.selectDevice(wizard.devices.value.first()) }
            scenario.awaitTrue("the pump is paired", PAIR_TIMEOUT) { wizard.step.value == AaruyPairStep.COMPLETE }
            scenario.awaitQueueIdle()
            scenario.awaitTrue("the pump is ready") { testGraphs.activePlugin.activePump.isInitialized() }
        } finally {
            scenario.onMain { viewModels.clear() }
        }
    }

    /** As the overview's unpair: through the queue, then the pump is forgotten. */
    override fun unpair(scenario: PumpScenario) {
        val result = runBlocking { scenario.commandQueue.customCommand(AaruyUnpairCommand()) }
        check(result.success) { "Unpair failed: ${result.comment}" }
        scenario.awaitQueueIdle()
        scenario.awaitTrue("no pump is paired") { !accessors.aaruyPlugin.isConfigured() }
    }

    override fun restoreEmulator(saved: ByteArray, emulators: PumpEmulators) {
        emulators.getOrCreate(AaruyPumpEmulator.KEY) { AaruyPumpEmulator.restore(saved, keyProvider = { aaruyFrameKey() }) }
    }

    private companion object {

        /** A real pump needs its button, so the driver tries again after 30 s. The emulator answers at once. */
        const val PAIR_TIMEOUT = 60_000L
    }
}
