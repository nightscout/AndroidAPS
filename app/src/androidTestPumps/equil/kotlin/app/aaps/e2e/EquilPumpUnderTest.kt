package app.aaps.e2e

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.di.testGraphs
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.compose.EquilWizardStep
import app.aaps.pump.equil.compose.EquilWizardViewModel
import app.aaps.pump.equil.compose.EquilWorkflow
import app.aaps.pump.equil.di.EquilAccessors
import app.aaps.pump.equil.emulator.EquilEmulatorBleTransport
import app.aaps.pump.equil.emulator.EquilPumpEmulator
import app.aaps.pump.equil.keys.EquilIntPreferenceKey

/**
 * The Equil pump for [PumpScenario]. The pod is activated and removed as the wizard does it, with the wizard's own
 * ViewModel from the app's factory and no screen. Its screens have their own test, [EquilUiTest].
 */
class EquilPumpUnderTest : PumpUnderTest {

    override val emulatorOption = ExternalOptions.EMULATE_EQUIL
    override val pluginName: String = EquilPumpPlugin::class.java.simpleName

    /** A first guess with room, set from what a run shows. */
    override val maxCommandsPerStatusRead = 15

    /**
     * The driver works out a stopped bolus from the times and rounds it up to the 0.05 U step. When the stop got no
     * answer, the times come from the pump log, which has whole seconds: up to 2 s more of the bolus speed. At the
     * standard 1.5 U/min that is 0.1 U, at the 12 U/min of the fast runs 0.45 U.
     */
    override val bolusOvercount: Double
        get() = 2.0 / testGraphs.preferences.get(EquilIntPreferenceKey.EquilBolusSpeed) + BOLUS_STEP

    private val accessors get() = testGraphs.rootGraph as EquilAccessors

    override fun control(): PumpEmulatorControl =
        checkNotNull(accessors.equilBleTransport as? EquilEmulatorBleTransport) { "The Equil emulator is not active" }

    /** The plugin's start reads the pod state, and the manager has none before it. */
    override fun awaitStarted(scenario: PumpScenario) {
        scenario.awaitTrue("the Equil pod state is read") { accessors.equilManager.equilState != null }
    }

    override fun pair(scenario: PumpScenario) {
        // The pump takes the bolus time in each bolus command. A user setting, so the fast runs use its fastest value.
        val speed = if (scenario.speed == EmulatorSpeed.FAST) FAST_SECONDS_PER_UNIT else EquilIntPreferenceKey.EquilBolusSpeed.defaultValue
        testGraphs.preferences.put(EquilIntPreferenceKey.EquilBolusSpeed, speed)
        withWizard(scenario, EquilWorkflow.PAIR) { wizard ->
            fun awaitStep(step: EquilWizardStep, timeoutMs: Long = PumpScenario.STEP_TIMEOUT) =
                scenario.awaitTrue("the wizard is at $step (it is at ${wizard.wizardStep.value})", timeoutMs) { wizard.wizardStep.value == step }

            awaitStep(EquilWizardStep.ASSEMBLE)
            scenario.onMain { wizard.moveToNextStep(EquilWizardStep.ASSEMBLE) }
            awaitStep(EquilWizardStep.BLE_SCAN)
            scenario.onMain {
                wizard.prepareBLEScan()
                wizard.startDeviceScan()
            }
            scenario.awaitTrue("the emulated pod is found") { wizard.scannedDevices.value.isNotEmpty() }
            scenario.onMain { wizard.onDeviceSelected(wizard.scannedDevices.value.first()) }
            awaitStep(EquilWizardStep.PASSWORD)
            scenario.onMain { wizard.startPairing(PAIR_PASSWORD) }
            awaitStep(EquilWizardStep.FILL, PAIR_TIMEOUT)
            scenario.onMain { wizard.startFill() }
            scenario.awaitTrue("the pod is filled", PAIR_TIMEOUT) { wizard.fillComplete.value }
            scenario.onMain { wizard.finishFill() }
            awaitStep(EquilWizardStep.ATTACH)
            scenario.onMain { wizard.moveToNextStep(EquilWizardStep.ATTACH) }
            awaitStep(EquilWizardStep.AIR)
            scenario.onMain { wizard.startAirRemoval() }
            scenario.awaitTrue("the air is out", PAIR_TIMEOUT) { wizard.airRemovalDone.value }
            scenario.onMain { wizard.finishAirStep() }
            awaitStep(EquilWizardStep.CONFIRM, PAIR_TIMEOUT)
            scenario.onMain { wizard.startConfirm() }
            scenario.awaitTrue("the pod is activated", ACTIVATION_TIMEOUT) { accessors.equilManager.isActivationCompleted() }
        }
        scenario.awaitQueueIdle()
    }

    /** As the wizard removes a pod: detach, then unpair. Equil counts as configured also without a pod. */
    override fun unpair(scenario: PumpScenario) {
        withWizard(scenario, EquilWorkflow.UNPAIR) { wizard ->
            scenario.awaitTrue("the wizard is at the detach step") { wizard.wizardStep.value == EquilWizardStep.UNPAIR_DETACH }
            scenario.onMain { wizard.startUnpairDetach() }
            scenario.awaitTrue("the wizard is at the unpair step", PAIR_TIMEOUT) { wizard.wizardStep.value == EquilWizardStep.UNPAIR_CONFIRM }
            scenario.onMain { wizard.confirmUnpair() }
            scenario.awaitTrue("no pod is paired", PAIR_TIMEOUT) { accessors.equilManager.equilState?.serialNumber.isNullOrEmpty() }
        }
        scenario.awaitQueueIdle()
    }

    override fun restoreEmulator(saved: ByteArray, emulators: PumpEmulators) {
        emulators.getOrCreate(EquilPumpEmulator.KEY) { EquilPumpEmulator.restore(saved) }
    }

    /** The wizard's ViewModel from the app's factory, started at [workflow], and cleared after [block]. */
    private fun withWizard(scenario: PumpScenario, workflow: EquilWorkflow, block: (EquilWizardViewModel) -> Unit) {
        val viewModels = ViewModelStore()
        try {
            lateinit var wizard: EquilWizardViewModel
            scenario.onMain {
                wizard = ViewModelProvider(viewModels, testGraphs.viewModelFactory).get(EquilWizardViewModel::class.java)
                wizard.initializeWorkflow(workflow)
            }
            block(wizard)
        } finally {
            scenario.onMain { viewModels.clear() }
        }
    }

    private companion object {

        /** The fastest bolus speed of the setting: 12 U/min, tested on a real pump. */
        const val FAST_SECONDS_PER_UNIT = 5

        /** The driver rounds a stopped bolus up to this step. */
        const val BOLUS_STEP = 0.05

        /** The pod knows no password before it is paired, so any one works. */
        const val PAIR_PASSWORD = "0000"

        /** A step of the wizard can be several pump commands with pauses between them. */
        const val PAIR_TIMEOUT = 60_000L
        const val ACTIVATION_TIMEOUT = 90_000L
    }
}
