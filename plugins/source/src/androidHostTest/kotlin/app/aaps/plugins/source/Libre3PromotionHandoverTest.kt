package app.aaps.plugins.source

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.PromotionResult
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.libre3.Libre3CgmDrivers
import app.aaps.plugins.libre3.Libre3GlucoseSample
import app.aaps.plugins.libre3.Libre3GlucoseWatcher
import app.aaps.plugins.libre3.identity.Libre3SensorIdentity
import app.aaps.plugins.libre3.identity.Libre3SensorStore
import app.aaps.plugins.libre3.session.Libre3MacArbiter
import app.aaps.shared.tests.SharedPreferencesMock
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * What must survive the moment the loop changes sensor.
 *
 * Two failures are guarded here. Both left the user with a connected sensor and a loop with no
 * glucose, and nothing on screen to say why:
 *
 * - a reading of the **retired** sensor that lands after the swap and writes its own high minute
 *   counter into the file of the sensor that has just taken over. Every reading of the new sensor
 *   is then refused as "already seen", in memory and again after every restart, because that
 *   counter starts at zero on a new sensor;
 * - a promotion cut off between the production write and the pre-soak wipe, which leaves one sensor
 *   in both slot files, so the next launch is a race for one sensor.
 */
class Libre3PromotionHandoverTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config
    @Mock lateinit var context: Context
    @Mock lateinit var persistenceLayer: PersistenceLayer


    private val notificationManager: NotificationManager = mock()

    /** Promotion tells the active calibration plugin to drop the retired sensor's fingersticks. */
    private val activeCalibration: Calibration = mock()
    private val activePlugin: ActivePlugin = mock<ActivePlugin>().also { whenever(it.activeCalibration).thenReturn(activeCalibration) }

    private val productionPrefs: SharedPreferences = SharedPreferencesMock()
    private val stagingPrefs: SharedPreferences = SharedPreferencesMock()

    private lateinit var plugin: Libre3NativePlugin

    private fun productionStore() = Libre3SensorStore(context, null)
    private fun stagingStore() = Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE)

    @BeforeEach
    fun setup() {
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSharedPreferences(PRODUCTION_PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(productionPrefs)
        whenever(context.getSharedPreferences(STAGING_PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(stagingPrefs)
        runTest {
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        Libre3Ingest.reset()
        Libre3MacArbiter.reset()
        Libre3CgmDrivers.releaseStagingInstance()?.let { runCatching { it.shutdown() } }
        plugin = Libre3NativePlugin(
            rh, aapsLogger, preferences, config, notificationManager, context, persistenceLayer, activePlugin,
        )
    }

    @AfterEach
    fun tearDownDrivers() {
        // The driver choice, the pre-soak instance and the sensor claims are process wide, so none
        // of them may travel to the next test.
        Libre3CgmDrivers.releaseStagingInstance()?.let { runCatching { it.shutdown() } }
        runCatching { Libre3CgmDrivers.default().shutdown() }
        Libre3MacArbiter.reset()
    }

    /** The state an app restart leaves behind: a running sensor deep into its life. */
    private fun storeRunningSensorLateInItsLife() {
        assertThat(productionStore().saveIdentityAndWait(RUNNING)).isTrue()
        productionStore().saveLastLifeCount(OLD_LIFE_COUNT)
        Libre3Ingest.seed(OLD_LIFE_COUNT, emptyList())
    }

    private fun startPresoak() {
        assertThat(stagingStore().saveIdentityAndWait(STAGED)).isTrue()
        assertThat(plugin.beginStaging(STAGED)).isTrue()
    }

    private fun reading(lifeCount: Int, mgdl: Double = 120.0) = Libre3GlucoseSample(
        mgdl = mgdl,
        timestampMs = STAGED.activatedAtMs + lifeCount * 60_000L,
        lifeCount = lifeCount,
    )

    // ---------------- The retired sensor must not poison the new one ----------------

    @Test
    fun `promotion tells calibration to drop the retired sensor's fingersticks`() = runTest {
        // The sensor change is dated at the pre-soak NFC activation, hours before this swap, so the
        // new sensor's session covers fingersticks that were taken on the sensor just retired.
        storeRunningSensorLateInItsLife()
        startPresoak()

        assertThat(plugin.promoteStagingToProduction()).isEqualTo(PromotionResult.Ok)

        verify(activeCalibration).ignoreEntriesBefore(any())
    }

    @Test
    fun `a reading of the retired sensor after a promotion changes no ingest mark`() = runTest {
        storeRunningSensorLateInItsLife()
        val retiredWatcher: Libre3GlucoseWatcher = plugin.productionWatcher
        startPresoak()

        assertThat(plugin.promoteStagingToProduction()).isEqualTo(PromotionResult.Ok)
        // The old sensor's last reading was already on its way when the user tapped Promote.
        retiredWatcher.onGlucose(reading(OLD_LIFE_COUNT + 1))

        assertThat(Libre3Ingest.lastAcceptedLifeCount()).isEqualTo(NO_LIFE_COUNT)
        assertThat(productionStore().loadLastLifeCount()).isEqualTo(NO_LIFE_COUNT)
    }

    @Test
    fun `the promoted sensor is still accepted after a late reading of the retired one`() = runTest {
        storeRunningSensorLateInItsLife()
        val retiredWatcher: Libre3GlucoseWatcher = plugin.productionWatcher
        startPresoak()
        assertThat(plugin.promoteStagingToProduction()).isEqualTo(PromotionResult.Ok)
        retiredWatcher.onGlucose(reading(OLD_LIFE_COUNT + 1))

        // A pre-soak sensor counts its own minutes from zero, so its counter is far below the one
        // the retired sensor had. Before the fix this reading, and every later one, was refused.
        plugin.onGlucose(reading(NEW_LIFE_COUNT, mgdl = 133.0))

        assertThat(Libre3Ingest.lastAcceptedLifeCount()).isEqualTo(NEW_LIFE_COUNT)
        verify(persistenceLayer, timeout(SLOW_INSERT_MS)).insertCgmSourceData(
            eq(Sources.Libre3Native), any(), any(), anyOrNull(),
        )
        awaitStoredLifeCount(NEW_LIFE_COUNT)
    }

    @Test
    fun `a reading of the retired sensor after a promotion never reaches the database`() = runTest {
        storeRunningSensorLateInItsLife()
        val retiredWatcher: Libre3GlucoseWatcher = plugin.productionWatcher
        startPresoak()
        assertThat(plugin.promoteStagingToProduction()).isEqualTo(PromotionResult.Ok)

        retiredWatcher.onGlucose(reading(OLD_LIFE_COUNT + 1))

        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `a reading of the old sensor after a plain sensor change changes no ingest mark`() {
        // The same hole on the path that has nothing to do with the pre-soak: the user scans a
        // different sensor while the old one is still delivering.
        storeRunningSensorLateInItsLife()
        val retiredWatcher: Libre3GlucoseWatcher = plugin.productionWatcher
        assertThat(productionStore().saveIdentityAndWait(STAGED)).isTrue()

        plugin.onSensorChanged()
        retiredWatcher.onGlucose(reading(OLD_LIFE_COUNT + 1))

        assertThat(Libre3Ingest.lastAcceptedLifeCount()).isEqualTo(NO_LIFE_COUNT)
        assertThat(productionStore().loadLastLifeCount()).isEqualTo(NO_LIFE_COUNT)
    }

    // ---------------- One sensor must never be picked up by both slots ----------------

    @Test
    fun `a pre-soak that is the sensor already feeding the loop is not picked up again`() {
        // What a promotion cut off between `adopt` and the pre-soak wipe leaves behind.
        assertThat(productionStore().saveIdentityAndWait(STAGED)).isTrue()
        assertThat(stagingStore().saveIdentityAndWait(STAGED)).isTrue()
        stagingStore().saveSlotProgress(present = true, validReadingCount = 10)

        assertThat(plugin.resumeStagingSessionIfStored()).isFalse()

        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
        // No second driver instance and no second Bluetooth thread was built for it, so the two
        // slots never race for one sensor in the first place.
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNull()
        // The leftover file is gone, so the next launch is not a race either.
        assertThat(stagingStore().loadIdentity()).isNull()
        assertThat(stagingStore().loadSlotPresent()).isFalse()
        // Production keeps its sensor, untouched.
        assertThat(productionStore().loadIdentity()!!.serialNumber).isEqualTo(STAGED.serialNumber)
    }

    @Test
    fun `a different pre-soak sensor is still picked up again`() {
        assertThat(productionStore().saveIdentityAndWait(RUNNING)).isTrue()
        assertThat(stagingStore().saveIdentityAndWait(STAGED)).isTrue()
        stagingStore().saveSlotProgress(present = true, validReadingCount = 10)

        assertThat(plugin.resumeStagingSessionIfStored()).isTrue()

        assertThat(plugin.stagingState.value).isNotEqualTo(StagingState.ABSENT)
        assertThat(stagingStore().loadIdentity()!!.serialNumber).isEqualTo(STAGED.serialNumber)
    }

    @Test
    fun `a slot flag without a sensor is cleared so the warning does not repeat`() {
        // A pre-soak progress write that landed after the promotion had wiped the file.
        stagingStore().saveSlotProgress(present = true, validReadingCount = 4)

        assertThat(plugin.resumeStagingSessionIfStored()).isFalse()

        // Before the fix the flag stayed on the disk and the same warning was written on every
        // launch for ever.
        assertThat(stagingStore().loadSlotPresent()).isFalse()
        assertThat(plugin.resumeStagingSessionIfStored()).isFalse()
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNull()
    }

    @Test
    fun `a promotion leaves the pre-soak slot flag off`() = runTest {
        assertThat(productionStore().saveIdentityAndWait(RUNNING)).isTrue()
        startPresoak()
        repeat(5) { plugin.stagingWatcher.onGlucose(reading(100 + it)) }

        assertThat(plugin.promoteStagingToProduction()).isEqualTo(PromotionResult.Ok)

        assertThat(stagingStore().loadSlotPresent()).isFalse()
        assertThat(stagingStore().loadIdentity()).isNull()
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
    }

    /** The mark is written on a background scope, so the test waits for it instead of guessing. */
    private fun awaitStoredLifeCount(expected: Int) {
        val giveUpAtMs = System.currentTimeMillis() + SLOW_INSERT_MS
        while (System.currentTimeMillis() < giveUpAtMs) {
            if (productionStore().loadLastLifeCount() == expected) return
            Thread.sleep(POLL_MS)
        }
        assertThat(productionStore().loadLastLifeCount()).isEqualTo(expected)
    }

    companion object {

        private const val PRODUCTION_PREFS_NAME = "libre3_sensor_store"
        private const val STAGING_PREFS_NAME = "libre3_sensor_store_staging"

        /** How long a database insert on a background scope may take before the test gives up. */
        private const val SLOW_INSERT_MS = 5_000L

        private const val POLL_MS = 20L

        /** What a stored ingest mark holds when no reading of this sensor was accepted yet. */
        private const val NO_LIFE_COUNT = -1

        /** About twelve days of sensor life, which is where a sensor gets replaced. */
        private const val OLD_LIFE_COUNT = 18_000

        /** A pre-soak sensor counts its own minutes from zero, so it is far below the old one. */
        private const val NEW_LIFE_COUNT = 1_500

        private val RUNNING = Libre3SensorIdentity(
            serialNumber = "MH0RUNNING",
            bleAddress = "AA:BB:CC:DD:EE:01",
            blePin = byteArrayOf(1, 2, 3, 4),
            receiverId = 1234,
            generation = 0,
            warmupMinutes = 60,
            wearDurationMinutes = 14 * 24 * 60,
            activatedAtMs = 1_777_000_000_000L,
        )

        private val STAGED = Libre3SensorIdentity(
            serialNumber = "MH0PRESOAK",
            bleAddress = "AA:BB:CC:DD:EE:02",
            blePin = byteArrayOf(9, 8, 7, 6),
            receiverId = 4321,
            generation = 0,
            warmupMinutes = 60,
            wearDurationMinutes = 14 * 24 * 60,
            activatedAtMs = 1_777_216_508_000L,
        )
    }
}
