package app.aaps.e2e

import android.Manifest
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.EmulatedOptions
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danarv2.DanaRv2Plugin
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Drives the **DanaR family drivers** against the in-tree pump emulator, with no Bluetooth hardware:
 * `DanaRPlugin` / `DanaRKoreanPlugin` / `DanaRv2Plugin` → their execution service →
 * `EmulatorRfcommTransport`.
 *
 * The RFCOMM counterpart to [DanaRsEmulatorPumpTest]: three separate drivers here, rather than one
 * driver with three handshakes, so each variant gets its own connect. What they share is the reason
 * this cannot be a JVM test — an execution service is a dagger-android `DaggerService` that only
 * exists on a device (see [DanaRsEmulatorPumpTest] for the full argument).
 *
 * `DanaModules.provideRfcommTransport` picks the emulator, and the variant *also* decides which
 * plugin it auto-enables — so unlike the RS side, choosing the option here chooses the driver.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DanaREmulatorPumpTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var danaRPlugin: DanaRPlugin
    @Inject lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Inject lateinit var danaRv2Plugin: DanaRv2Plugin
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var pluginList: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var config: Config
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private var activePlugin: PluginBase? = null

    @Test
    fun danaR_connectsAgainstEmulator() {
        assertConnects(ExternalOptions.EMULATE_DANA_R) { danaRPlugin }
    }

    @Test
    fun danaRKorean_connectsAgainstEmulator() {
        assertConnects(ExternalOptions.EMULATE_DANA_R_KOREAN) { danaRKoreanPlugin }
    }

    @Test
    fun danaRv2_connectsAgainstEmulator() {
        assertConnects(ExternalOptions.EMULATE_DANA_R_V2) { danaRv2Plugin }
    }

    /**
     * Brings [plugin] up against the emulated [variant] and requires it to connect.
     *
     * Per test rather than in `@Before`: `RfcommTransport` is `@Singleton` and reads
     * `config.isEnabled` once, when the graph first constructs it, so the variant has to be chosen
     * before `hiltRule.inject()`. Each test method gets a fresh Hilt component, and so a fresh pump.
     */
    private fun assertConnects(variant: ExternalOptions, plugin: () -> PluginBase) {
        EmulatedOptions.enabled = setOf(variant)
        hiltRule.inject()

        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)

        // The emulated pump answers as this device, and the driver looks it up by name:
        // AbstractDanaRExecutionService.connect asks the transport for a socket for RName. Both are
        // set because DanaModules generates a random name when EmulatorDeviceName is empty, and
        // isConfigured() is false while RName is.
        preferences.put(DanaStringNonKey.EmulatorDeviceName, DEVICE_NAME)
        preferences.put(DanaStringNonKey.RName, DEVICE_NAME)

        // The plugin/config init MainApp.onCreate does, which the Hilt test app doesn't.
        pluginStore.plugins = pluginList
        config.initCompleted()

        val pump = plugin()
        activePlugin = pump
        // Runs onStart inline (unlike setPluginEnabled, which launches it on pluginScope), and
        // onStart is what binds the execution service. The service injects RfcommTransport, which
        // is what finally constructs the emulator — after the preferences above, deliberately.
        pump.setPluginEnabledBlocking(PluginType.PUMP, true)
        assertThat(pump.isEnabled()).isTrue()

        val asPump = pump as Pump
        assertThat(asPump.isConfigured()).isTrue()

        // bindService is async, so connect() is a no-op until the service lands — drive it until it
        // takes. Keeping the whole service lifetime inside this component's is also what stops it
        // outliving the test and crashing on a torn-down component (see DanaRsEmulatorPumpTest).
        val connected = awaitTrue(CONNECT_TIMEOUT) {
            asPump.connect("e2e")
            asPump.isConnected()
        }
        assertThat(connected).isTrue()
    }

    @After
    fun tearDown() {
        // Drain queued work before anything else: left running it keeps talking to the pump and
        // posts notifications into whichever test comes next, whose UI then recomposes under
        // uiautomator (SetupWizardE2EHiltTest died that way, CI build 40253).
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        runCatching { (activePlugin as? Pump)?.disconnect("test end") }
        // Unbind before the component dies — onStop calls unbindService.
        runCatching { activePlugin?.setPluginEnabledBlocking(PluginType.PUMP, false) }
        activePlugin = null
        // SharedPreferences outlive the Hilt component, so don't leave sibling tests in this
        // process believing a DanaR is configured.
        runCatching {
            preferences.remove(DanaStringNonKey.RName)
            preferences.remove(DanaStringNonKey.EmulatorDeviceName)
        }
        EmulatedOptions.enabled = emptySet()
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

        private const val PKG = "info.nightscout.androidaps"

        /** Shaped like the name DanaModules generates for an emulated DanaR ("DAN#####EM"). */
        private const val DEVICE_NAME = "DAN00001EM"
        private const val CONNECT_TIMEOUT = 40_000L
        private const val POLL_MS = 250L
    }
}
