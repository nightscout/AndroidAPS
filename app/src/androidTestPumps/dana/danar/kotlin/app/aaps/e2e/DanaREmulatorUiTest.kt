package app.aaps.e2e

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.ComposeMainActivity
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.di.ResetGraphRule
import app.aaps.di.testGraphs
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.keys.DanaIntNonKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danar.di.DanaRAccessors
import app.aaps.pump.danar.emulator.EmulatorRfcommTransport
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.testcategories.ShardB
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The RFCOMM counterpart to [DanaRsEmulatorUiTest]: drives the **DanaR family UI** against the in-tree
 * pump emulator with no Bluetooth hardware — UI → `CommandQueue` → `DanaRv2Plugin` →
 * `DanaRv2ExecutionService` → [EmulatorRfcommTransport].
 *
 * Only the DanaR pump page and its history read. The shared treatment dialogs get their one pass in
 * [DanaRsEmulatorUiTest], and the DanaR family's commands are tested through the queue by the
 * `DanaRScenarioTest`, `DanaRKoreanScenarioTest` and `DanaRv2ScenarioTest` classes. DanaR renders its own
 * `DanaRComposeContent`, so the pump is initialized through the queue rather than by navigating that
 * screen's labels.
 *
 * See [DanaREmulatorPumpTest] for the two RFCOMM-specific wiring notes reused here: the transport must
 * be resolved through a `Provider` (its provider eagerly enables the plugin, which needs plugins set),
 * and DanaR's non-idempotent `connect()` must not be re-called once connected.
 */
// @ShardB: part of the DanaR/RFCOMM family that runs together on emulator B - see ShardB for the balance.
@RunWith(AndroidJUnit4::class)
@ShardB
class DanaREmulatorUiTest : AbstractDanaEmulatorUiTest() {


    // RetryRule outermost: a flaky timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(ResetGraphRule())

    // A Provider, not the transport directly: provideRfcommTransport enables the target plugin
    // (storeSettings), which needs pluginStore.plugins - set only after the graph is built. Resolving it
    // lazily (first state read, well after bringUp) returns the @Singleton the service already built.
    private val rfcommTransport get() = (testGraphs.rootGraph as DanaRAccessors).rfcommTransport
    private val danaRv2Plugin get() = (testGraphs.rootGraph as DanaRAccessors).danaRv2Plugin

    // The emulated pump's state, resolved lazily: the transport is an EmulatorRfcommTransport whose
    // `emulator` (a DanaRPumpEmulator) holds the `state` the driver reads and writes.
    private val emulatorState by lazy { (rfcommTransport as EmulatorRfcommTransport).emulator.state }

    // ---- hooks -------------------------------------------------------------------------------------


    override fun seedPairedPump(variant: ExternalOptions) {
        // The emulated pump answers as this device; the driver looks it up by name.
        preferences.put(DanaStringNonKey.RName, DEVICE_NAME)
        preferences.put(DanaStringNonKey.EmulatorDeviceName, DEVICE_NAME)
        // Match the emulator's password (DanaRPumpState.password) or the pump reports "wrong password"
        // and never reaches isInitialized (isPasswordOK) - which the Manage actions gate on.
        preferences.put(DanaIntNonKey.Password, EMULATOR_PASSWORD)
        // Make DanaRv2 the active pump instead of the default Virtual Pump.
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_DanaRv2Plugin", value = true)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_VirtualPumpPlugin", value = false)
        // NOTE: the emulator state is not touched here - it cannot be resolved yet (provideRfcommTransport
        // needs pluginStore.plugins, set by the base after this hook). It is resolved lazily on first read.
    }

    override fun tearDownPump() {
        runCatching { (danaRv2Plugin as Pump).disconnect("test end") }
        runCatching { danaRv2Plugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
    }

    override fun requestStatusRead() {
        // DanaR has no changePump(); a status read through the queue connects, reads and marks the pump
        // initialized. Blocks until it completes (or fails, which the init loop then retries).
        runCatching { runBlocking { commandQueue.readStatus("e2e status read") } }
    }

    override fun assertActivePumpIsThisDana() {
        val pump = activePlugin.activePumpInternal as PluginBase
        assertThat(pump).isInstanceOf(DanaRv2Plugin::class.java)
        assertThat(pump.hasComposeContent()).isTrue()
    }

    // ---- test --------------------------------------------------------------------------------------

    /**
     * Reads the pump's **review history** through the UI, the way a user does: Manage → Pump → Pump
     * history → Refresh, which loads the type off the pump and renders it (`loadHistory` →
     * `MsgHistoryAlarm` → `MsgHistoryAll` → `DanaHistoryRecordDao` → `DanaHistoryScreen`). The screen
     * opens on Alarms (the first type), so a single seeded alarm auto-loads after Refresh. Mirrors
     * [DanaRsEmulatorUiTest]'s `visitDanaHistory` - the DanaR emulator now serves the per-type review
     * commands.
     */
    @Test
    fun danaR_readsHistoryFromUi() {
        bringUp(ExternalOptions.EMULATE_DANA_R_V2)
        assertActivePumpIsThisDana()
        val scenario = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            waitForOverview()
            initializePumpThroughQueue()                    // Pump history appears only once initialized
            // Seed AFTER connect: resolving emulatorState (the transport) before the pump is up eagerly
            // re-enables the plugin and can stall app startup (a first-launch ANR seen locally). One alarm
            // in the past - the driver asks for everything after a "from" instant and the store is strict.
            emulatorState.reviewHistoryStore.addEvent(
                RecordTypes.RECORD_TYPE_ALARM.toInt(), System.currentTimeMillis() - HISTORY_RECORD_AGE_MS, 0, ALARM_OCCLUSION
            )
            openDanaPlugin()                                // Manage → Pump → the DanaR overview
            waitForQueueIdle()
            openVia("Pump history", expect = "Alarms")      // DanaHistoryScreen, opens on Alarms
            openVia("Refresh", expect = HISTORY_ALARM_TEXT) // loads the seeded alarm off the pump
        } catch (t: Throwable) {
            logScreen("E2E_DANAR_HISTORY")
            throw t
        } finally {
            scenario.close()
        }
    }

    companion object {

        /** Shaped like the name DanaModules generates for an emulated DanaR ("DAN#####EM"). */
        private const val DEVICE_NAME = "DAN00001EM"
        private const val HISTORY_RECORD_AGE_MS = 60 * 60 * 1000L
        private const val ALARM_OCCLUSION = 79 // MsgHistoryAll decodes 79 → "Occlusion"
        private const val HISTORY_ALARM_TEXT = "Occlusion"
        /** Matches DanaRPumpState.password on the emulator. */
        private const val EMULATOR_PASSWORD = 1234
    }
}
