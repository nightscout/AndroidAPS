package app.aaps.e2e

import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.di.testGraphs
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.dash.OmnipodDashPumpPlugin
import app.aaps.pump.omnipod.dash.di.OmnipodDashAccessors
import app.aaps.pump.omnipod.dash.ui.wizard.compose.DashOmnipodWizardViewModel
import app.aaps.pump.omnipod.emulator.OmnipodEmulatorTransport
import app.aaps.pump.omnipod.emulator.PodEmulator

/** The Omnipod Dash for [PumpScenario]. See [AbstractOmnipodPumpUnderTest] for how a pod is activated and removed. */
class DashPumpUnderTest : AbstractOmnipodPumpUnderTest<DashOmnipodWizardViewModel>(DashOmnipodWizardViewModel::class.java) {

    override val emulatorOption = ExternalOptions.EMULATE_OMNIPOD_DASH
    override val pluginName: String = OmnipodDashPumpPlugin::class.java.simpleName

    private val accessors get() = testGraphs.rootGraph as OmnipodDashAccessors

    override fun control(): PumpEmulatorControl =
        checkNotNull(accessors.dashBleConnectionFactory as? OmnipodEmulatorTransport) { "The Omnipod Dash emulator is not active" }

    override fun activationProgress(): ActivationProgress = accessors.dashPodStateManager.activationProgress

    override fun restoreEmulator(saved: ByteArray, emulators: PumpEmulators) {
        emulators.getOrCreate(PodEmulator.KEY) { PodEmulator.restore(saved) }
    }
}
