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
import app.aaps.core.interfaces.pump.rfcomm.RfcommTransport
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.EmulatedOptions
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.testcategories.ShardA
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import javax.inject.Provider

/**
 * Drives the **non-delivery** DanaR-family operations against the in-tree RFCOMM emulator — a status
 * read, event/history load, TDD load, user-options write and a time update through each of the plain
 * DanaR, DanaR-Korean and DanaRv2 drivers — exercising the execution-service and plugin paths the
 * delivery/review-history tests in [DanaREmulatorPumpTest] don't reach.
 *
 * Deliberately a **separate class on `@ShardA`**, not more `@Test`s in `DanaREmulatorPumpTest`
 * (`@ShardB`): shard B already runs the DanaR family + RS pump/transport in one process, and piling
 * three more pump-connect tests onto it exhausted that process (CI build 40352 crashed on the third).
 * A fresh, lightly-loaded process here avoids the cumulative-state crash.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ShardA
class DanaROperationsTest {

    val hiltRule = HiltAndroidRule(this)

    // RetryRule outermost: a flaky timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(hiltRule)

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var danaRPlugin: DanaRPlugin
    @Inject lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Inject lateinit var danaRv2Plugin: DanaRv2Plugin
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var pluginList: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var config: Config
    @Inject lateinit var rfcommTransportProvider: Provider<RfcommTransport>
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var activePlugin: PluginBase? = null

    @Test
    fun danaR_readsStatusEventsAndOptions() {
        bringUpConnected(ExternalOptions.EMULATE_DANA_R) { danaRPlugin }
        runBlocking { commandQueue.readStatus("test") }
        runBlocking { commandQueue.loadEvents() }
        runBlocking { commandQueue.loadTDDs() }
        runBlocking { commandQueue.setUserOptions() }
        runBlocking { commandQueue.updateTime() }
    }

    @Test
    fun danaRKorean_readsStatusAndEvents() {
        bringUpConnected(ExternalOptions.EMULATE_DANA_R_KOREAN) { danaRKoreanPlugin }
        runBlocking { commandQueue.readStatus("test") }
        runBlocking { commandQueue.loadEvents() }
        runBlocking { commandQueue.loadTDDs() }
    }

    @Test
    fun danaRv2_readsStatusEventsAndOptions() {
        bringUpConnected(ExternalOptions.EMULATE_DANA_R_V2) { danaRv2Plugin }
        runBlocking { commandQueue.readStatus("test") }
        runBlocking { commandQueue.loadEvents() }
        runBlocking { commandQueue.loadTDDs() }
        runBlocking { commandQueue.setUserOptions() }
        runBlocking { commandQueue.updateTime() }
    }

    /**
     * Brings [plugin] up against the emulated [variant] and requires it to connect. Mirrors
     * `DanaREmulatorPumpTest.bringUpConnected` — variant chosen before inject() (`RfcommTransport` is a
     * `@Singleton` read once), plugin enabled inline so its execution service binds, connect polled
     * until the async service-bind lands.
     */
    private fun bringUpConnected(variant: ExternalOptions, plugin: () -> PluginBase): Pump {
        EmulatedOptions.enabled = setOf(variant)
        hiltRule.inject()

        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)
        preferences.put(DanaStringNonKey.EmulatorDeviceName, DEVICE_NAME)
        preferences.put(DanaStringNonKey.RName, DEVICE_NAME)

        pluginStore.plugins = pluginList
        config.initCompleted()

        val pump = plugin()
        activePlugin = pump
        pump.setPluginEnabledBlocking(PluginType.PUMP, true)
        assertThat(pump.isEnabled()).isTrue()

        val asPump = pump as Pump
        assertThat(asPump.isConfigured()).isTrue()
        val connected = awaitTrue(CONNECT_TIMEOUT) {
            if (!asPump.isConnected() && !asPump.isConnecting()) asPump.connect("e2e")
            asPump.isConnected()
        }
        assertThat(connected).isTrue()
        return asPump
    }

    @After
    fun tearDown() {
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        runCatching { (activePlugin as? Pump)?.disconnect("test end") }
        runCatching { activePlugin?.setPluginEnabledBlocking(PluginType.PUMP, false) }
        activePlugin = null
        runCatching {
            preferences.remove(DanaStringNonKey.RName)
            preferences.remove(DanaStringNonKey.EmulatorDeviceName)
        }
        EmulatedOptions.enabled = emptySet()
    }

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
        private const val DEVICE_NAME = "DAN00001EM"
        private const val CONNECT_TIMEOUT = 40_000L
        private const val POLL_MS = 250L
    }
}
