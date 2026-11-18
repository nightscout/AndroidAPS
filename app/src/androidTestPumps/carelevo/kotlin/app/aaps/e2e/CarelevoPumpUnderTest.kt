package app.aaps.e2e

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.di.testGraphs
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.pump.carelevo.CarelevoPumpPlugin
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.di.CarelevoAccessors
import app.aaps.pump.carelevo.emulator.CarelevoEmulatorBleTransport
import app.aaps.pump.carelevo.emulator.CarelevoPumpEmulator
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectNeedleEvent
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectPrepareEvent
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectSafetyCheckEvent
import app.aaps.pump.carelevo.presentation.model.CarelevoOverviewEvent
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoOverviewViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchNeedleInsertionViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchSafetyCheckViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.jvm.optionals.getOrNull

/**
 * The CareLevo patch for [PumpScenario]. It is activated as the wizard does it, with the wizard's own ViewModels and
 * no screen: scan and pair, the safety check, the needle check and the basal program. The ViewModels come from the
 * app's factory, so they are wired as in the app.
 */
class CarelevoPumpUnderTest : PumpUnderTest {

    override val emulatorOption = ExternalOptions.EMULATE_CARELEVO
    override val pluginName: String = CarelevoPumpPlugin::class.java.simpleName

    /** A status read connects, reads the patch and the alarms, and closes. Measured, with room. */
    override val maxCommandsPerStatusRead = 12

    private val accessors get() = testGraphs.rootGraph as CarelevoAccessors

    override fun control(): PumpEmulatorControl = accessors.carelevoBleTransport as CarelevoEmulatorBleTransport

    override fun awaitStarted(scenario: PumpScenario) {
        // Set by the plugin's start. The wizard refuses every step until it is.
        scenario.awaitTrue("the driver reports Bluetooth on") { accessors.carelevoPatch.isBluetoothEnabled() }
        scenario.awaitTrue("the user settings are loaded") { accessors.carelevoPatch.userSettingInfo.value?.getOrNull() != null }
    }

    override fun pair(scenario: PumpScenario) {
        val viewModels = ViewModelStore()
        val events = Collections.synchronizedList(mutableListOf<Any>())
        val listening = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            val provider = ViewModelProvider(viewModels, testGraphs.viewModelFactory)
            lateinit var connect: CarelevoPatchConnectViewModel
            lateinit var safety: CarelevoPatchSafetyCheckViewModel
            lateinit var needle: CarelevoPatchNeedleInsertionViewModel
            scenario.onMain {
                connect = provider.get(CarelevoPatchConnectViewModel::class.java)
                safety = provider.get(CarelevoPatchSafetyCheckViewModel::class.java)
                needle = provider.get(CarelevoPatchNeedleInsertionViewModel::class.java)
            }
            listening.collectInto(events, connect.event, safety.event, needle.event)

            scenario.onMain { connect.startScan() }
            awaitEvent(events, CarelevoConnectPrepareEvent.ShowConnectDialog, CarelevoConnectPrepareEvent.ShowMessageScanFailed)
            scenario.onMain { connect.startConnect(PATCH_INSULIN) }
            awaitEvent(events, CarelevoConnectPrepareEvent.ConnectComplete, CarelevoConnectPrepareEvent.ConnectFailed)

            scenario.onMain { safety.startSafetyCheck() }
            awaitEvent(events, CarelevoConnectSafetyCheckEvent.SafetyCheckComplete, CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)

            scenario.onMain { needle.startCheckNeedle() }
            awaitEvent(events, CarelevoConnectNeedleEvent.CheckNeedleComplete(true), CarelevoConnectNeedleEvent.CheckNeedleError)
            scenario.onMain { needle.startSetBasal() }
            awaitEvent(
                events, CarelevoConnectNeedleEvent.SetBasalComplete,
                CarelevoConnectNeedleEvent.SetBasalFailed, CarelevoConnectNeedleEvent.ShowMessageProfileNotSet
            )

            scenario.awaitQueueIdle()
            scenario.awaitTrue("the patch is active") { testGraphs.activePlugin.activePump.isInitialized() }
        } finally {
            listening.cancel()
            scenario.onMain { viewModels.clear() }
        }
    }

    /**
     * As the user discards the patch on the overview screen: a stop through the queue, and when the patch cannot be
     * reached, the patch is removed from AAPS anyway.
     */
    override fun unpair(scenario: PumpScenario) {
        val viewModels = ViewModelStore()
        val events = Collections.synchronizedList(mutableListOf<Any>())
        val listening = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            lateinit var overview: CarelevoOverviewViewModel
            scenario.onMain { overview = ViewModelProvider(viewModels, testGraphs.viewModelFactory).get(CarelevoOverviewViewModel::class.java) }
            listening.collectInto(events, overview.event)
            scenario.onMain { overview.startDiscardProcess() }
            val end = SystemClock.uptimeMillis() + DISCARD_TIMEOUT
            while (accessors.carelevoPatch.patchState.value?.getOrNull() != PatchState.NotConnectedNotBooting) {
                check(CarelevoOverviewEvent.DiscardFailed !in events) { "The discard failed. Events: $events" }
                check(SystemClock.uptimeMillis() < end) { "The patch is still there. Events: $events" }
                SystemClock.sleep(PumpScenario.POLL_MS)
            }
        } finally {
            listening.cancel()
            scenario.onMain { viewModels.clear() }
        }
        scenario.awaitQueueIdle()
    }

    override fun restoreEmulator(saved: ByteArray, emulators: PumpEmulators) {
        emulators.getOrCreate(CarelevoPumpEmulator.KEY) { CarelevoPumpEmulator.restore(saved) }
    }

    private fun CoroutineScope.collectInto(events: MutableList<Any>, vararg flows: Flow<Any>) {
        flows.forEach { flow -> launch { flow.collect { events += it } } }
    }

    /** Waits for [expected], and fails at once on a [failures] event, which the wizard would show as an error. */
    private fun awaitEvent(events: List<Any>, expected: Any, vararg failures: Any) {
        val end = SystemClock.uptimeMillis() + STEP_TIMEOUT
        while (SystemClock.uptimeMillis() < end) {
            failures.firstOrNull { it in events }?.let { error("The wizard reported $it. Events: $events") }
            if (expected in events) return
            SystemClock.sleep(PumpScenario.POLL_MS)
        }
        error("Timeout waiting for $expected. Events: $events")
    }

    private companion object {

        /** Units filled into the new patch. */
        const val PATCH_INSULIN = 200

        /** Pairing includes a 5 s scan, and a step can retry once. */
        const val STEP_TIMEOUT = 60_000L

        /** A patch that cannot be reached: the queue gives up on the link, then the patch is removed anyway. */
        const val DISCARD_TIMEOUT = 90_000L
    }
}
