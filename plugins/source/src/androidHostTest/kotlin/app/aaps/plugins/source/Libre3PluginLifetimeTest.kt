package app.aaps.plugins.source

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.PromotionRejectReason
import app.aaps.core.interfaces.source.PromotionResult
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.libre3.Libre3CgmDriverReal
import app.aaps.plugins.libre3.Libre3CgmDrivers
import app.aaps.plugins.libre3.Libre3GlucoseSample
import app.aaps.plugins.libre3.identity.Libre3SensorIdentity
import app.aaps.plugins.libre3.session.Libre3DisconnectPolicy
import app.aaps.plugins.libre3.session.Libre3MacArbiter
import app.aaps.shared.tests.SharedPreferencesMock
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * When the work of the Libre 3 plugin runs, and which driver it listens to.
 *
 * Nothing may run for a user who never enables the plugin, and nothing may keep running after
 * `onStop`. The state flows must still answer while the plugin is off. And after a stop and a new
 * start, the plugin must listen to the new production driver, because the old one was shut down.
 */
class Libre3PluginLifetimeTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config
    @Mock lateinit var context: Context
    @Mock lateinit var persistenceLayer: PersistenceLayer

    private val notificationManager: NotificationManager = mock()
    private val activePlugin: ActivePlugin = mock()

    private val productionPrefs: SharedPreferences = SharedPreferencesMock()
    private val stagingPrefs: SharedPreferences = SharedPreferencesMock()

    private lateinit var plugin: Libre3NativePlugin

    @BeforeEach
    fun setup() = runTest {
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSharedPreferences(PRODUCTION_PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(productionPrefs)
        whenever(context.getSharedPreferences(STAGING_PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(stagingPrefs)
        whenever(persistenceLayer.getBgReadingsDataFromTime(any(), any())).thenReturn(emptyList())
        Libre3Ingest.reset()
        Libre3MacArbiter.reset()
        plugin = Libre3NativePlugin(
            rh, aapsLogger, preferences, config, notificationManager, context, persistenceLayer, activePlugin,
        )
    }

    @AfterEach
    fun tearDown() {
        Libre3CgmDrivers.releaseStagingInstance()?.let { runCatching { it.shutdown() } }
        runCatching { Libre3CgmDrivers.default().shutdown() }
        Libre3MacArbiter.reset()
        Libre3Ingest.reset()
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
    fun `nothing keeps running after onStop`() = runTest {
        plugin.onStart()
        // A lost link arms the reconnect watchdog: a coroutine that waits five minutes.
        plugin.onSession(up = false, reason = Libre3DisconnectPolicy.Reason.LINK_LOST.name)
        assertThat(plugin.hasRunningWork()).isTrue()

        plugin.onStop()

        assertThat(plugin.hasRunningWork()).isFalse()
    }

    @Test
    fun `a database write still waiting is cancelled by onStop`() = runTest {
        // The write of a reading never returns, so only onStop can end it.
        whenever(persistenceLayer.insertCgmSourceData(any(), any(), any(), anyOrNull())).doSuspendableAnswer { awaitCancellation() }
        plugin.onStart()
        plugin.onGlucose(Libre3GlucoseSample(mgdl = 120.0, timestampMs = System.currentTimeMillis(), lifeCount = 900))
        assertThat(plugin.hasRunningWork()).isTrue()

        plugin.onStop()

        assertThat(plugin.hasRunningWork()).isFalse()
    }

    @Test
    fun `a start after a stop gets working again`() = runTest {
        plugin.onStart()
        plugin.onStop()

        plugin.onStart()
        plugin.onSession(up = false, reason = Libre3DisconnectPolicy.Reason.LINK_LOST.name)

        assertThat(plugin.hasRunningWork()).isTrue()
        plugin.onStop()
        assertThat(plugin.hasRunningWork()).isFalse()
    }

    // How the three tests below see a delivery: a lost link arms the reconnect watchdog (running
    // work), and a disconnect from the driver tells its watchers that the user asked for it, which
    // cancels the watchdog. So the watchdog stops only if the driver really has the plugin as a
    // watcher. The warm-up state cannot be used here: it posts a notification, which needs a real
    // Android, see Libre3PluginWarmupStatusTest.

    private fun armWatchdog() {
        plugin.onSession(up = false, reason = Libre3DisconnectPolicy.Reason.LINK_LOST.name)
        assertThat(plugin.hasRunningWork()).isTrue()
    }

    @Test
    fun `the plugin uses the real driver and listens to it`() = runTest {
        plugin.onStart()
        val driver = Libre3CgmDrivers.default()
        assertThat(driver).isInstanceOf(Libre3CgmDriverReal::class.java)
        armWatchdog()

        driver.disconnect()

        assertThat(plugin.hasRunningWork()).isFalse()
        plugin.onStop()
    }

    @Test
    fun `after onStop and onStart the rebuilt driver delivers to the plugin`() = runTest {
        plugin.onStart()
        val first = Libre3CgmDrivers.default()
        plugin.onStop()
        assertThat((first as Libre3CgmDriverReal).isShutDown()).isTrue()

        plugin.onStart()
        val rebuilt = Libre3CgmDrivers.default()
        assertThat(rebuilt).isNotSameInstanceAs(first)
        armWatchdog()

        rebuilt.disconnect()

        assertThat(plugin.hasRunningWork()).isFalse()
        plugin.onStop()
    }

    @Test
    fun `a driver that is not attached does not reach the plugin`() = runTest {
        // The control case of the test above: a driver rebuilt behind the plugin's back has no
        // watcher, so its disconnect does not reach the plugin and the watchdog keeps running.
        plugin.onStart()
        Libre3CgmDrivers.default().shutdown()
        val rebuiltWithoutStart = Libre3CgmDrivers.default()
        armWatchdog()

        rebuiltWithoutStart.disconnect()

        assertThat(plugin.hasRunningWork()).isTrue()
        plugin.onStop()
    }

    @Test
    fun `late driver callbacks after onStop start no work`() = runTest {
        plugin.onStart()
        plugin.onStop()

        plugin.onSession(up = false, reason = Libre3DisconnectPolicy.Reason.LINK_LOST.name)
        plugin.onGlucose(Libre3GlucoseSample(mgdl = 120.0, timestampMs = System.currentTimeMillis(), lifeCount = 901))

        assertThat(plugin.isStopped()).isTrue()
        assertThat(plugin.hasWorkScope()).isFalse()
        assertThat(plugin.isReconnectWatchdogArmed()).isFalse()
        assertThat(plugin.hasRunningWork()).isFalse()
    }

    @Test
    fun `user actions after onStop are refused and build or connect no driver`() = runTest {
        plugin.onStart()
        plugin.onStop()

        plugin.connectStoredSensor("AA:BB:CC:DD:EE:FF")
        assertThat(plugin.beginStaging(STAGED)).isFalse()
        plugin.connectStagingSensor(STAGED.bleAddress)
        assertThat(plugin.promoteStagingToProduction(allowEarly = true))
            .isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_ABSENT))

        // No driver was built for any of them, so none can have connected.
        assertThat(Libre3CgmDrivers.productionOrNull()).isNull()
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNull()
        assertThat(plugin.hasWorkScope()).isFalse()
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
    }

    @Test
    fun `onStop finishes its work when a step throws`() = runTest {
        plugin.onStart()
        val production = Libre3CgmDrivers.productionOrNull()
        armWatchdog()
        // The last step before the work cancel.
        whenever(context.stopService(any())).thenThrow(SecurityException("refused"))

        plugin.onStop()

        assertThat((production as Libre3CgmDriverReal).isShutDown()).isTrue()
        assertThat(plugin.hasWorkScope()).isFalse()
        assertThat(plugin.hasRunningWork()).isFalse()
    }

    @Test
    fun `onStop of a plugin that never started builds no driver`() = runTest {
        // Leaves the registry with no live production instance, as on a fresh app start.
        runCatching { Libre3CgmDrivers.default().shutdown() }
        assertThat(Libre3CgmDrivers.productionOrNull()).isNull()

        plugin.onStop()

        assertThat(Libre3CgmDrivers.productionOrNull()).isNull()
    }

    companion object {

        private val STAGED = Libre3SensorIdentity(
            serialNumber = "MH0STOPPED",
            bleAddress = "AA:BB:CC:DD:EE:03",
            blePin = byteArrayOf(9, 8, 7, 6),
            receiverId = 4321,
            generation = 0,
            warmupMinutes = 60,
            wearDurationMinutes = 14 * 24 * 60,
            activatedAtMs = 1_777_216_508_000L,
        )

        private const val PRODUCTION_PREFS_NAME = "libre3_sensor_store"
        private const val STAGING_PREFS_NAME = "libre3_sensor_store_staging"
    }
}
