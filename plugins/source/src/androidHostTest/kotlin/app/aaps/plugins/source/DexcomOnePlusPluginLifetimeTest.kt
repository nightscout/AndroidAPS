package app.aaps.plugins.source

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriver
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriverStub
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseWatcher
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorPrefs
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScanner
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScannerStub
import app.aaps.shared.tests.AAPSLoggerTest
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * When the work of the Dexcom ONE+ plugin runs.
 *
 * Nothing may run for a user who never enables the plugin, and nothing may keep running after
 * `onStop`. The state flows must still answer while the plugin is off, because the overview reads
 * them for every BG source.
 */
class DexcomOnePlusPluginLifetimeTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var activePlugin: ActivePlugin

    private val warmupBasalGuard: DexcomOnePlusWarmupBasalGuard = mock()
    private lateinit var driver: OnePlusCgmDriverStub
    private lateinit var plugin: DexcomOnePlusPlugin

    @BeforeEach
    fun setup() = runTest {
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), any())).thenReturn(emptyList())
        driver = OnePlusCgmDriverStub()
        plugin = DexcomOnePlusPlugin(
            rh, AAPSLoggerTest(), preferences, config, notificationManager, FakeHost(driver), persistenceLayer,
            warmupBasalGuard, activePlugin, rxBus,
        )
    }

    @Test
    fun `a plugin that was never started runs nothing and its flows still answer`() {
        assertThat(plugin.hasRunningWork()).isFalse()
        assertThat(plugin.warmupStatus.value).isNull()
        assertThat(plugin.lifecycle.value).isNull()
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
        assertThat(plugin.stagingLifecycle.value).isNull()
        assertThat(plugin.stagingEvidence.value).isNull()
        assertThat(plugin.stagingSettleRemainingMs.value).isNull()
    }

    @Test
    fun `the warm-up status follows the driver without a running collector`() {
        plugin.onWarmup(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.WARMING, remainingMs = 60_000L))

        assertThat(plugin.warmupStatus.value).isNotNull()
        assertThat(plugin.hasRunningWork()).isFalse()

        plugin.onWarmup(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.READY))

        assertThat(plugin.warmupStatus.value).isNull()
    }

    @Test
    fun `nothing keeps running after onStop`() = runTest {
        plugin.onStart()
        // A lost link arms the reconnect watchdog: a coroutine that waits five minutes.
        plugin.onSession(up = false, reason = "link lost")
        assertThat(plugin.hasRunningWork()).isTrue()

        plugin.onStop()

        assertThat(plugin.hasRunningWork()).isFalse()
    }

    @Test
    fun `a database write still waiting is cancelled by onStop`() = runTest {
        // The write of a reading never returns, so only onStop can end it. The watchdog is not
        // involved here: onStop cancels the watchdog on its own, this checks the rest of the work.
        whenever(persistenceLayer.insertCgmSourceData(any(), any(), any(), anyOrNull())).doSuspendableAnswer { awaitCancellation() }
        plugin.onStart()
        plugin.onGlucose(OnePlusGlucoseSample(mgdl = 120.0, timestampMs = System.currentTimeMillis(), sequence = 900_001L))
        assertThat(plugin.hasRunningWork()).isTrue()

        plugin.onStop()

        assertThat(plugin.hasRunningWork()).isFalse()
    }

    @Test
    fun `a start after a stop gets working again`() = runTest {
        plugin.onStart()
        plugin.onStop()

        plugin.onStart()
        plugin.onSession(up = false, reason = "link lost")

        assertThat(plugin.hasRunningWork()).isTrue()
        plugin.onStop()
        assertThat(plugin.hasRunningWork()).isFalse()
    }

    @Test
    fun `the basal guard is asked on each warm-up or connection change while the plugin runs`() = runTest {
        plugin.onStart()

        plugin.onWarmup(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.WARMING, remainingMs = 60_000L))
        verifyBlocking(warmupBasalGuard, timeout(5_000L).times(1)) { ensureProfileBasalDuringWarmup(eq(plugin), any(), any()) }
        // A reconnect with only a new status text is a change too: the glucose may be older by now.
        // Each change is awaited before the next one: the state is a StateFlow, and two changes sent
        // at once may be merged into one before the collector sees them.
        plugin.onWarmup(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.RECONNECTING, message = "attempt 1"))
        verifyBlocking(warmupBasalGuard, timeout(5_000L).times(2)) { ensureProfileBasalDuringWarmup(eq(plugin), any(), any()) }
        plugin.onWarmup(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.RECONNECTING, message = "attempt 2"))
        verifyBlocking(warmupBasalGuard, timeout(5_000L).times(3)) { ensureProfileBasalDuringWarmup(eq(plugin), any(), any()) }

        plugin.onStop()
    }

    @Test
    fun `the basal guard runs only between onStart and onStop`() = runTest {
        plugin.onWarmup(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.WARMING, remainingMs = 60_000L))
        plugin.onStart()
        verifyBlocking(warmupBasalGuard, timeout(5_000L).times(1)) { ensureProfileBasalDuringWarmup(eq(plugin), any(), any()) }
        assertThat(plugin.isWarmupGuardRunning()).isTrue()
        plugin.onStop()

        // No collector and no job are left after onStop, so nothing can call the guard again.
        assertThat(plugin.isWarmupGuardRunning()).isFalse()
        assertThat(plugin.hasRunningWork()).isFalse()
        plugin.onWarmup(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.RECONNECTING, message = "after stop"))

        verifyBlocking(warmupBasalGuard, times(1)) { ensureProfileBasalDuringWarmup(any(), any(), any()) }
    }

    /** Everything the plugin asks of the phone, answered without Bluetooth or a file. */
    private class FakeHost(private val driver: OnePlusCgmDriver) : DexcomOnePlusHost {

        private val stores = mutableMapOf<String?, OnePlusSensorStore>()

        override fun attachPlatform(driver: OnePlusCgmDriver) = Unit
        override fun slotStore(namespace: String?): OnePlusSensorStore =
            stores.getOrPut(namespace) { OnePlusSensorStore(MemoryPrefs()) }

        override val stagingNamespace: String = "staging"
        override fun productionDriver(): OnePlusCgmDriver = driver
        override fun stagingDriver(): OnePlusCgmDriver = driver
        override fun attachProductionDriver(watcher: OnePlusGlucoseWatcher): OnePlusCgmDriver =
            driver.also { it.addWatcher(watcher) }

        override fun promoteStagingInstance(): OnePlusCgmDriver? = null
        override fun setSessionServiceWanted(wanted: Boolean) = Unit
        override fun showWarmup(state: OnePlusWarmupState) = Unit
        override fun cancelWarmupNotification() = Unit
        override fun alert(message: String) = Unit
        override fun screenScanner(): OnePlusBleScanner = OnePlusBleScannerStub()
        override fun screenItems(plugin: DexcomOnePlusPlugin): List<PreferenceItem> = emptyList()
    }

    /** An empty slot store in memory. */
    private class MemoryPrefs : OnePlusSensorPrefs {

        private val values = mutableMapOf<String, Any>()

        override fun getString(key: String): String? = values[key] as? String
        override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
        override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(block: OnePlusSensorPrefs.Editor.() -> Unit) {
            object : OnePlusSensorPrefs.Editor {
                override fun putString(key: String, value: String?) {
                    if (value == null) values.remove(key) else values[key] = value
                }

                override fun putLong(key: String, value: Long) {
                    values[key] = value
                }

                override fun putInt(key: String, value: Int) {
                    values[key] = value
                }

                override fun putBoolean(key: String, value: Boolean) {
                    values[key] = value
                }

                override fun remove(key: String) {
                    values.remove(key)
                }

                override fun clear() {
                    values.clear()
                }
            }.block()
        }
    }
}
