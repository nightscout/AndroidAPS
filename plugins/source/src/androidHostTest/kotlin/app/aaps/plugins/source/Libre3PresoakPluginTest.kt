package app.aaps.plugins.source

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.PromotionRejectReason
import app.aaps.core.interfaces.source.PromotionResult
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.libre3.Libre3CgmDriverReal
import app.aaps.plugins.libre3.Libre3CgmDrivers
import app.aaps.plugins.libre3.Libre3GlucoseSample
import app.aaps.plugins.libre3.Libre3LogMarkers
import app.aaps.shared.tests.AAPSLoggerTest
import app.aaps.plugins.libre3.identity.Libre3SensorIdentity
import app.aaps.plugins.libre3.identity.Libre3SensorStore
import app.aaps.shared.tests.SharedPreferencesMock
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
 * The pre-soak slot collects, and it does not feed the loop until a promotion succeeds.
 *
 * A test here fails if a pre-soak reading calls [PersistenceLayer.insertCgmSourceData]. Promotion
 * success, the cutoff and the refusals that must write nothing are in [Libre3PromotionTest] and
 * [Libre3PromotionHandoverTest]. With no pre-soak sensor, promotion is still
 * [PromotionRejectReason.STAGING_ABSENT].
 */
class Libre3PresoakPluginTest : TestBase() {

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

    private val stagedActivatedAtMs = 1_777_216_508_000L
    private val staged = Libre3SensorIdentity(
        serialNumber = "MH0PRESOAK",
        bleAddress = "AA:BB:CC:DD:EE:02",
        blePin = byteArrayOf(9, 8, 7, 6),
        receiverId = 4321,
        generation = 0,
        warmupMinutes = 60,
        wearDurationMinutes = 14 * 24 * 60,
        activatedAtMs = stagedActivatedAtMs,
    )

    private fun sample(index: Int) = Libre3GlucoseSample(
        mgdl = 100.0 + (index % 50),
        timestampMs = stagedActivatedAtMs + index * 60_000L,
        lifeCount = 100 + index,
    )

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
        plugin = newPlugin()
    }

    @AfterEach
    fun tearDownDrivers() {
        Libre3CgmDrivers.releaseStagingInstance()?.let { runCatching { it.shutdown() } }
        runCatching { Libre3CgmDrivers.default().shutdown() }
    }

    private fun newPlugin() = Libre3NativePlugin(
        rh, aapsLogger, preferences, config, notificationManager, context, persistenceLayer, activePlugin,
    )

    private fun storeStagedSensor() {
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).saveIdentityAndWait(staged)).isTrue()
    }

    private fun startPresoak() {
        storeStagedSensor()
        assertThat(plugin.beginStaging(staged)).isTrue()
    }

    private fun feed(count: Int) {
        repeat(count) { plugin.stagingWatcher.onGlucose(sample(it)) }
    }

    @Test
    fun `beginStaging accepts a sensor that is not the one feeding the loop`() {
        startPresoak()

        assertThat(plugin.stagingState.value).isEqualTo(StagingState.WARMUP)
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNotNull()
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadSlotPresent()).isTrue()
    }

    @Test
    fun `a pre-soak on the sensor that already feeds the loop is refused`() {
        assertThat(Libre3SensorStore(context, null).saveIdentityAndWait(staged)).isTrue()
        storeStagedSensor()

        assertThat(plugin.beginStaging(staged)).isFalse()
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNull()
    }

    @Test
    fun `the pre-soak needs no setting and knows its own sensor`() {
        assertThat(plugin.isStagingSensor(staged.serialNumber, staged.bleAddress)).isFalse()

        startPresoak()

        assertThat(plugin.isStagingSensor(staged.serialNumber, null)).isTrue()
        assertThat(plugin.isStagingSensor(null, staged.bleAddress)).isTrue()
        assertThat(plugin.isProductionSensor(staged.serialNumber, staged.bleAddress)).isFalse()
    }

    @Test
    fun `beginStaging returns false when the pre-soak file cannot be opened`() {
        // `beginStaging` wraps the slot write and returns false from `getOrElse`. A throw must not
        // be reported as a started slot.
        whenever(context.getSharedPreferences(STAGING_PREFS_NAME, Context.MODE_PRIVATE))
            .thenThrow(IllegalStateException("disk"))

        assertThat(plugin.beginStaging(staged)).isFalse()
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
    }

    @Test
    fun `cancelling a pre-soak still clears the slot when shutdown throws`() {
        // `cancelStaging` wraps `shutdown` in `runCatching`. A throw must not skip the wipe, and it
        // must not escape.
        assertThat(Libre3SensorStore(context, null).saveIdentityAndWait(PRODUCTION_SENSOR)).isTrue()
        val before = HashMap(productionPrefs.all)
        val failing = mock<Libre3CgmDriverReal>()
        whenever(failing.shutdown()).thenThrow(IllegalStateException("shutdown"))
        val field = Libre3CgmDrivers::class.java.getDeclaredField("stagingReal")
        field.isAccessible = true
        field.set(Libre3CgmDrivers, failing)

        plugin.cancelStaging()

        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNull()
        assertThat(productionPrefs.all).isEqualTo(before)
        verify(failing).shutdown()
    }

    @Test
    fun `cancelling a pre-soak frees the slot and leaves production alone`() {
        assertThat(Libre3SensorStore(context, null).saveIdentityAndWait(PRODUCTION_SENSOR)).isTrue()
        startPresoak()
        feed(3)

        plugin.cancelStaging()

        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
        assertThat(plugin.stagingEvidence.value).isNull()
        assertThat(plugin.stagingCurve.value).isEmpty()
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNull()
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadSlotPresent()).isFalse()
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadIdentity()).isNull()
        assertThat(Libre3SensorStore(context, null).loadIdentity()!!.serialNumber)
            .isEqualTo(PRODUCTION_SENSOR.serialNumber)
    }

    @Test
    fun `a different pre-soak sensor is picked up again after a restart`() {
        assertThat(Libre3SensorStore(context, null).saveIdentityAndWait(PRODUCTION_SENSOR)).isTrue()
        startPresoak()
        feed(3)
        Libre3CgmDrivers.releaseStagingInstance()?.shutdown()

        val restarted = newPlugin()

        assertThat(restarted.resumeStagingSessionIfStored()).isTrue()
        assertThat(restarted.stagingEvidence.value!!.validCount).isEqualTo(3)
        assertThat(restarted.stagingCurve.value).isEmpty()
        assertThat(restarted.stagingState.value).isEqualTo(StagingState.SETTLING)
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadIdentity()!!.serialNumber)
            .isEqualTo(staged.serialNumber)
    }

    @Test
    fun `a pre-soak that is the sensor already feeding the loop is not picked up again`() {
        assertThat(Libre3SensorStore(context, null).saveIdentityAndWait(staged)).isTrue()
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).saveIdentityAndWait(staged)).isTrue()
        Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE)
            .saveSlotProgress(present = true, validReadingCount = 10)

        assertThat(plugin.resumeStagingSessionIfStored()).isFalse()

        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNull()
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadIdentity()).isNull()
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadSlotPresent()).isFalse()
        assertThat(Libre3SensorStore(context, null).loadIdentity()!!.serialNumber).isEqualTo(staged.serialNumber)
    }

    @Test
    fun `a slot flag without a sensor is cleared so the warning does not repeat`() {
        Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE)
            .saveSlotProgress(present = true, validReadingCount = 4)

        assertThat(plugin.resumeStagingSessionIfStored()).isFalse()

        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadSlotPresent()).isFalse()
        assertThat(plugin.resumeStagingSessionIfStored()).isFalse()
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNull()
    }

    @Test
    fun `resume returns false when the pre-soak file cannot be read`() {
        // Same `getOrElse` as `beginStaging`: a throw is a missed resume, not a started slot.
        whenever(context.getSharedPreferences(STAGING_PREFS_NAME, Context.MODE_PRIVATE))
            .thenThrow(IllegalStateException("disk"))

        assertThat(plugin.resumeStagingSessionIfStored()).isFalse()
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
    }

    @Test
    fun `a staging sample never reaches the persistence layer`() {
        startPresoak()

        feed(10)

        runBlocking {
            verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
        }
    }

    @Test
    fun `a staging sample never moves the production dedup floor`() {
        Libre3Ingest.seed(4242, emptyList())
        startPresoak()

        feed(10)

        assertThat(Libre3Ingest.lastAcceptedLifeCount()).isEqualTo(4242)
    }

    @Test
    fun `a production sample still reaches the persistence layer`() = runTest {
        plugin.onGlucose(
            Libre3GlucoseSample(mgdl = 110.0, timestampMs = stagedActivatedAtMs + 3_600_000L, lifeCount = 5),
        )

        verify(persistenceLayer, timeout(SLOW_INSERT_MS)).insertCgmSourceData(
            eq(Sources.Libre3Native), any(), any(), anyOrNull(),
        )
    }

    @Test
    fun `staging samples are visible`() {
        startPresoak()

        feed(10)

        val evidence = plugin.stagingEvidence.value!!
        assertThat(evidence.validCount).isEqualTo(10)
        assertThat(evidence.lastValueMgdl).isEqualTo(sample(9).mgdl)
        assertThat(evidence.lastValueAtEpochMs).isEqualTo(sample(9).timestampMs)
        assertThat(plugin.stagingCurve.value.map { it.timestampMs })
            .containsExactlyElementsIn((0..9).map { sample(it).timestampMs })
            .inOrder()
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.READY)
    }

    @Test
    fun `the curve is capped`() {
        startPresoak()

        feed(Libre3Staging.CURVE_CAP + 50)

        assertThat(plugin.stagingCurve.value).hasSize(Libre3Staging.CURVE_CAP)
        assertThat(plugin.stagingCurve.value.first().timestampMs).isEqualTo(sample(50).timestampMs)
    }

    @Test
    fun `promotion stays refused when no staging sensor is present`() = runTest {
        assertThat(plugin.promoteStagingToProduction())
            .isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_ABSENT))
        assertThat(plugin.promoteStagingToProduction(allowEarly = true))
            .isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_ABSENT))

        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `refreshing the session service does not throw when the sensor store cannot be read`() {
        // `refreshSessionService` wraps the whole body in `runCatching` and logs the failure. The
        // call returns normally. This test fails if that failure leaves the function.
        whenever(context.getSharedPreferences(PRODUCTION_PREFS_NAME, Context.MODE_PRIVATE))
            .thenThrow(IllegalStateException("prefs"))

        plugin.refreshSessionService()
    }

    @Test
    fun `an exception launched on ioScope is logged and does not escape`() = runBlocking {
        // The work scope carries a CoroutineExceptionHandler. Without it a throw inside launch
        // reaches the process handler and takes the app down.
        val logger = RecordingLogger()
        val watched = newPlugin(logger)
        val scope = watched.javaClass.getDeclaredMethod("ioScope").apply { isAccessible = true }
            .invoke(watched) as CoroutineScope
        val escaped = AtomicReference<Throwable?>(null)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, thrown -> escaped.set(thrown) }
        try {
            val job = scope.launch { throw IllegalStateException("boom") }
            withTimeout(5_000) { job.join() }
            assertThat(escaped.get()).isNull()
            assertThat(logger.errors.single()).contains("${Libre3LogMarkers.ERROR}: background work failed, boom")
            assertThat(logger.throwables.single()).isInstanceOf(IllegalStateException::class.java)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            scope.coroutineContext[Job]?.cancel()
        }
    }

    private fun newPlugin(logger: AAPSLogger) = Libre3NativePlugin(
        rh, logger, preferences, config, notificationManager, context, persistenceLayer, activePlugin,
    )

    private class RecordingLogger : AAPSLogger by AAPSLoggerTest() {

        val errors = mutableListOf<String>()
        val throwables = mutableListOf<Throwable>()

        override fun error(tag: LTag, message: String, throwable: Throwable) {
            errors += message
            throwables += throwable
        }
    }

    companion object {

        private const val PRODUCTION_PREFS_NAME = "libre3_sensor_store"
        private const val STAGING_PREFS_NAME = "libre3_sensor_store_staging"
        private const val SLOW_INSERT_MS = 5_000L

        private val PRODUCTION_SENSOR = Libre3SensorIdentity(
            serialNumber = "MH0RUNNING",
            bleAddress = "AA:BB:CC:DD:EE:01",
            blePin = byteArrayOf(1, 2, 3, 4),
            receiverId = 1234,
            generation = 0,
            warmupMinutes = 60,
            wearDurationMinutes = 14 * 24 * 60,
            activatedAtMs = 1_777_000_000_000L,
        )
    }
}
