package app.aaps.e2e

import android.Manifest
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.EmulatedOptions
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.dana.keys.DanaStringComposedKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.emulator.EmulatorBleTransport
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64
import javax.inject.Inject

/**
 * Drives the **real Dana-i driver** against the in-tree pump emulator, with no Bluetooth hardware:
 * `DanaRSPlugin` → `DanaRSService` → `BLEComm` → [EmulatorBleTransport].
 *
 * ## What this covers that the JVM tests don't
 * `:pump:danars-emulator`'s own `BLECommBLE5IntegrationTest` already drives `BLEComm` against the
 * emulator directly, with a mocked `Preferences` and no plugin. It cannot cover what sits above:
 * `DanaRSPlugin.connect` only works once `DanaRSService` is **bound** ([DanaRSPlugin] binds it in
 * `onStart` via `Context.bindService`), and `DanaRSService` is a dagger-android `DaggerService`
 * needing a real Android component + `HasAndroidInjector` — neither of which exists off-device.
 * So this is the first test of the plugin/service layer at all.
 *
 * ## All three RS handshakes
 * `connect_completes*HandshakeAgainstEmulator` covers each encryption the emulator speaks — BLE5
 * (Dana-i) with a stored key, v3 with negotiated keys, v1 by pairing from scratch. They differ only
 * below `BLEComm`, so the rest of the assertions here run on BLE5 alone rather than three times
 * over. The seeded values mirror the JVM `BLEComm*IntegrationTest`s, which pin both sides the same.
 *
 * The DanaR family (`EMULATE_DANA_R`/`_KOREAN`/`_V2`) is not here: those are different plugins over
 * `RfcommTransport`, not this one — see `DanaREmulatorPumpTest`.
 *
 * The emulator is selected purely by the `EMULATE_*` option — see [EmulatedOptions] for why a test
 * has to report that rather than drop the production marker file.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DanaRsEmulatorPumpTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var bleTransport: BleTransport
    @Inject lateinit var danaRSPlugin: DanaRSPlugin
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var pluginList: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var config: Config
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private lateinit var emulator: EmulatorBleTransport
    private var serviceBound = false

    /**
     * Brings the driver up against the emulated [variant] and blocks until its service is bound.
     *
     * Called from each test rather than `@Before` because the variant has to be chosen *before*
     * `hiltRule.inject()` — `BleTransport` is `@Singleton`, so `DanaModules` reads
     * `config.isEnabled` once, when the graph first constructs it. `HiltAndroidRule` builds a fresh
     * component per test method, so each test gets its own pump.
     */
    private fun bringUpPump(variant: ExternalOptions) {
        EmulatedOptions.enabled = setOf(variant)
        hiltRule.inject()

        // BLEComm.connect gates on BLUETOOTH_CONNECT before it ever reaches the transport, so an
        // emulated pump needs it granted just as a real one does — without it connect() only logs
        // "missing permission" and returns false.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)

        // The pump the driver believes it is paired to. The emulator answers on whatever address is
        // passed to gatt.connect(), but the plugin refuses to connect with either field blank
        // (DanaRSPlugin.connect), and BLEComm looks its keys up by device *name*.
        preferences.put(DanaStringNonKey.RsName, DEVICE_NAME)
        preferences.put(DanaStringNonKey.MacAddress, DEVICE_ADDRESS)
        preferences.put(DanaStringNonKey.Password, PASSWORD)

        emulator = bleTransport as EmulatorBleTransport
        emulator.pairingDelayMs = 0
        emulator.writeLatencyMs = 0
        seedPairingFor(variant)

        // The plugin/config init MainApp.onCreate does, which the Hilt test app doesn't.
        pluginStore.plugins = pluginList
        config.initCompleted()

        // setPluginEnabledBlocking (@TestOnly) runs onStart via runBlocking, unlike
        // setPluginEnabled/performPluginSwitch which launch it on pluginScope. onStart is what
        // binds DanaRSService.
        danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        // Loads mDeviceAddress/mDeviceName from the preferences seeded above.
        danaRSPlugin.changePump()

        // bindService is asynchronous: Android creates DanaRSService on the main looper. It is a
        // dagger-android DaggerService, so its onCreate injects through BaseTestApp.androidInjector
        // -> the *current test's* Hilt component. If the test method finished first, HiltAndroidRule
        // would already have torn that component down and the service would crash the process with
        // "The component was not created". So block here until the service is actually up, keeping
        // its whole lifetime inside the component's.
        //
        // There is no "service bound" signal on the plugin, so drive connect() until it takes
        // effect: it is a no-op while danaRSService is null.
        serviceBound = awaitTrue(BIND_TIMEOUT) {
            danaRSPlugin.connect("e2e bind")
            danaRSPlugin.isConnecting() || danaRSPlugin.isConnected()
        }
    }

    /**
     * Whatever each handshake needs to complete without a human.
     *
     * Mirrors the JVM-side `BLEComm*IntegrationTest`s, which pin the same values on both sides.
     * V1 is absent deliberately: with no stored pairing key `BLEComm` sends a pairing request, the
     * emulator confirms it and returns the key — so the pairing path itself gets exercised, which
     * is the interesting half of that variant.
     */
    private fun seedPairingFor(variant: ExternalOptions) {
        when (variant) {
            ExternalOptions.EMULATE_DANA_BLE5  -> {
                preferences.put(DanaStringComposedKey.Ble5PairingKey, DEVICE_NAME, value = BLE5_PAIRING_KEY)
                // Pump side of the same key — a mismatch fails the handshake rather than the assertion.
                emulator.pumpState.ble5PairingKey = BLE5_PAIRING_KEY
            }

            // Encrypted with these, so app and pump must agree or every later packet is garbage.
            // Seeding them is also what skips the PIN screen (BLEComm.sendV3PairingInformation
            // launches EnterPinActivity when either key is missing, which no test can answer).
            ExternalOptions.EMULATE_DANA_RS_V3 -> {
                val encoder = Base64.getEncoder()
                val state = emulator.pumpState
                preferences.put(
                    DanaStringComposedKey.V3ParingKey, DEVICE_NAME,
                    value = encoder.encodeToString(state.v3PairingKey)
                )
                preferences.put(
                    DanaStringComposedKey.V3RandomParingKey, DEVICE_NAME,
                    value = encoder.encodeToString(state.v3RandomPairingKey)
                )
                preferences.put(
                    DanaStringComposedKey.V3RandomSyncKey, DEVICE_NAME,
                    value = String.format("%02x", state.v3RandomSyncKey)
                )
            }

            else                               -> Unit
        }
    }

    @After
    fun tearDown() {
        // changePump() fires commandQueue.readStatus when the pump is configured, which runs a real
        // connection through a QueueWorker. That work is not scoped to this test — left running it
        // keeps talking to the pump and posting notifications into whichever test comes next, whose
        // UI then recomposes under uiautomator (SetupWizardE2EHiltTest died with a
        // StaleObjectException that way, CI build 40253). Drain it before anything else.
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        runCatching { danaRSPlugin.disconnect("test end") }
        // Unbind before the component dies — see the note in setUp. onStop calls unbindService.
        runCatching { danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
        // SharedPreferences outlive the Hilt component, so don't leave sibling instrumented tests
        // in this process believing a Dana pump is configured.
        runCatching {
            preferences.remove(DanaStringNonKey.RsName)
            preferences.remove(DanaStringNonKey.MacAddress)
            preferences.remove(DanaStringNonKey.Password)
            preferences.remove(DanaStringComposedKey.Ble5PairingKey, DEVICE_NAME)
            // Cleared unconditionally: v1 stores a key it paired for itself, and a key left over
            // from one variant would change how the next test's handshake starts.
            preferences.remove(DanaStringComposedKey.ParingKey, DEVICE_NAME)
            preferences.remove(DanaStringComposedKey.V3ParingKey, DEVICE_NAME)
            preferences.remove(DanaStringComposedKey.V3RandomParingKey, DEVICE_NAME)
            preferences.remove(DanaStringComposedKey.V3RandomSyncKey, DEVICE_NAME)
        }
        EmulatedOptions.enabled = emptySet()
    }

    @Test
    fun bleTransport_isTheEmulator() {
        bringUpPump(ExternalOptions.EMULATE_DANA_BLE5)
        assertThat(bleTransport).isInstanceOf(EmulatorBleTransport::class.java)
    }

    @Test
    fun plugin_isConfiguredFromSeededPreferences() {
        bringUpPump(ExternalOptions.EMULATE_DANA_BLE5)
        assertThat(danaRSPlugin.mDeviceName).isEqualTo(DEVICE_NAME)
        assertThat(danaRSPlugin.isConfigured()).isTrue()
    }

    @Test
    fun danaRSService_binds() {
        // Separated from the handshake assertion so a binding failure and a protocol failure are
        // distinguishable: this one failing means the service never came up at all.
        bringUpPump(ExternalOptions.EMULATE_DANA_BLE5)
        assertThat(serviceBound).isTrue()
    }

    // One per handshake the emulator speaks. They share everything above the transport, so what
    // each actually proves is that its own encryption and pairing survive the real plugin/service
    // path: BLE5 with a stored key, v3 with negotiated keys, v1 by pairing from scratch.

    @Test
    fun connect_completesBle5HandshakeAgainstEmulator() {
        assertConnects(ExternalOptions.EMULATE_DANA_BLE5)
    }

    @Test
    fun connect_completesV1HandshakeAgainstEmulator() {
        assertConnects(ExternalOptions.EMULATE_DANA_RS_V1)
    }

    @Test
    fun connect_completesV3HandshakeAgainstEmulator() {
        assertConnects(ExternalOptions.EMULATE_DANA_RS_V3)
    }

    private fun assertConnects(variant: ExternalOptions) {
        bringUpPump(variant)
        assertThat(serviceBound).isTrue()
        val connected = awaitTrue(CONNECT_TIMEOUT) {
            danaRSPlugin.connect("e2e")
            danaRSPlugin.isConnected()
        }
        assertThat(connected).isTrue()
    }

    /** Polls [condition] until it returns true or [timeoutMs] elapses. */
    private fun awaitTrue(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return true
            SystemClock.sleep(POLL_MS)
        }
        return false
    }

    companion object {

        // Mirrors BLECommBLE5IntegrationTest so both sides of the protocol are pinned identically.
        // DEVICE_NAME must match EmulatorBleTransport's default: DanaModules builds it without an
        // explicit name, and only a startScan (which this test does not do) would change it.
        private const val PKG = "info.nightscout.androidaps"
        private const val DEVICE_NAME = "UHH00002TI"
        private const val DEVICE_ADDRESS = "00:00:00:00:00:00"
        private const val BLE5_PAIRING_KEY = "474632"
        private const val PASSWORD = "0000"
        private const val BIND_TIMEOUT = 20_000L
        private const val CONNECT_TIMEOUT = 30_000L
        private const val POLL_MS = 250L
    }
}
