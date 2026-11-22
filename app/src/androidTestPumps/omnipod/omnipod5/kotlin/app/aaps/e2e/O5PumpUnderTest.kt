package app.aaps.e2e

import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.di.testGraphs
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.emulator.PodEmulator
import app.aaps.pump.omnipod.omnipod5.O5PumpPlugin
import app.aaps.pump.omnipod.omnipod5.di.O5Accessors
import app.aaps.pump.omnipod.omnipod5.di.O5EmulatorRadio
import app.aaps.pump.omnipod.omnipod5.ui.wizard.compose.O5OmnipodWizardViewModel

/** The Omnipod 5 for [PumpScenario]. See [AbstractOmnipodPumpUnderTest] for how a pod is activated and removed. */
class O5PumpUnderTest : AbstractOmnipodPumpUnderTest<O5OmnipodWizardViewModel>(O5OmnipodWizardViewModel::class.java) {

    override val emulatorOption = ExternalOptions.EMULATE_OMNIPOD_5
    override val pluginName: String = O5PumpPlugin::class.java.simpleName

    private val accessors get() = testGraphs.rootGraph as O5Accessors

    override fun control(): PumpEmulatorControl =
        checkNotNull(accessors.o5Radio as? O5EmulatorRadio) { "The Omnipod 5 emulator is not active" }.transport

    override fun activationProgress(): ActivationProgress = accessors.o5PodStateManager.activationProgress

    override fun restoreEmulator(saved: ByteArray, emulators: PumpEmulators) {
        emulators.getOrCreate(PodEmulator.KEY_OMNIPOD_5) { PodEmulator.restore(saved, productId = PodEmulator.PRODUCT_ID_OMNIPOD_5) }
    }
}
