package app.aaps.plugins.source

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.calibration.CalibrationContext
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.PromotionRejectReason
import app.aaps.core.interfaces.source.PromotionResult
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.libre3.Libre3CgmDriver
import app.aaps.plugins.libre3.Libre3CgmDriverReal
import app.aaps.plugins.libre3.Libre3CgmDrivers
import app.aaps.plugins.libre3.Libre3LogMarkers
import app.aaps.plugins.libre3.Libre3GlucoseSample
import app.aaps.plugins.libre3.identity.Libre3SensorIdentity
import app.aaps.plugins.libre3.identity.Libre3SensorStore
import app.aaps.plugins.libre3.session.Libre3MacArbiter
import app.aaps.shared.tests.AAPSLoggerTest
import app.aaps.shared.tests.SharedPreferencesMock
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
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
 * Promotion of a Libre 3 pre-soak, including every refusal that must leave the loop's sensor
 * untouched.
 *
 * The success cases check that the promoted sensor feeds the loop. The refusal cases check every
 * guard in front of `ignoreEntriesBefore`.
 */
class Libre3PromotionTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config
    @Mock lateinit var context: Context
    @Mock lateinit var persistenceLayer: PersistenceLayer


    private val notificationManager: NotificationManager = mock()
    private val activeCalibration: Calibration = mock()

    private val productionPrefs: SharedPreferences = SharedPreferencesMock()
    private val stagingGate = GatedPreferences(SharedPreferencesMock())

    private lateinit var plugin: Libre3NativePlugin

    /** The production driver when the test started. A refused promotion must leave it in place. */
    private lateinit var productionDriverAtStart: Libre3CgmDriver

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

    private val running = Libre3SensorIdentity(
        serialNumber = "MH0RUNNING",
        bleAddress = "AA:BB:CC:DD:EE:01",
        blePin = byteArrayOf(1, 2, 3, 4),
        receiverId = 1234,
        generation = 0,
        warmupMinutes = 60,
        wearDurationMinutes = 14 * 24 * 60,
        activatedAtMs = 1_777_000_000_000L,
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
        whenever(context.getSharedPreferences(STAGING_PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(stagingGate)
        runTest {
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        Libre3Ingest.reset()
        Libre3MacArbiter.reset()
        plugin = newPlugin()
        productionDriverAtStart = Libre3CgmDrivers.default()
    }

    @AfterEach
    fun tearDownDrivers() {
        Libre3CgmDrivers.releaseStagingInstance()?.let { runCatching { it.shutdown() } }
        runCatching { Libre3CgmDrivers.default().shutdown() }
        Libre3MacArbiter.reset()
        stagingGate.throwOnSerial = false
        stagingGate.throwOnSessionKeys = false
    }

    private fun newPlugin(
        logger: AAPSLogger = aapsLogger,
        calibration: Calibration = activeCalibration,
    ): Libre3NativePlugin {
        val plugins = mock<ActivePlugin>()
        whenever(plugins.activeCalibration).thenReturn(calibration)
        return Libre3NativePlugin(
            rh, logger, preferences, config, notificationManager, context, persistenceLayer, plugins,
        )
    }

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

    private fun seedRunningSensor(): HashMap<String, Any?> {
        assertThat(Libre3SensorStore(context, null).saveIdentityAndWait(running)).isTrue()
        Libre3SensorStore(context, null).saveLastLifeCount(9_000)
        Libre3SensorStore(context, null).saveSensorChangeLoggedSerial(running.serialNumber)
        return HashMap(productionPrefs.all)
    }

    private fun assertNothingIrreversible(productionBefore: Map<String, *>) {
        runBlocking {
            verify(activeCalibration, never()).ignoreEntriesBefore(any())
            verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
        }
        assertThat(productionPrefs.all).isEqualTo(productionBefore)
        // The driver that feeds the loop is the same instance, and it is still alive.
        assertThat(Libre3CgmDrivers.default()).isSameInstanceAs(productionDriverAtStart)
        assertThat((productionDriverAtStart as Libre3CgmDriverReal).isShutDown()).isFalse()
    }

    @Test
    fun `after promotion the same path does publish`() = runTest {
        startPresoak()
        feed(10)

        assertThat(plugin.promoteStagingToProduction()).isEqualTo(PromotionResult.Ok)

        plugin.onGlucose(Libre3GlucoseSample(mgdl = 123.0, timestampMs = stagedActivatedAtMs + 3_600_000L, lifeCount = 900))
        verify(persistenceLayer, timeout(SLOW_INSERT_MS)).insertCgmSourceData(
            eq(Sources.Libre3Native), any(), any(), anyOrNull(),
        )
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
        assertThat(plugin.stagingCurve.value).isEmpty()
    }

    @Test
    fun `promotion has no soak gate`() = runTest {
        // A missing pairing key is a log, not a refusal.
        startPresoak()
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadSessionKeys().phase5RawKey).isNull()

        assertThat(plugin.promoteStagingToProduction(allowEarly = false)).isEqualTo(PromotionResult.Ok)

        verify(activeCalibration).ignoreEntriesBefore(any())
    }

    @Test
    fun `allowEarly does not change a promotion that has a sensor`() = runTest {
        startPresoak()

        assertThat(plugin.promoteStagingToProduction(allowEarly = true)).isEqualTo(PromotionResult.Ok)

        verify(activeCalibration).ignoreEntriesBefore(any())
    }

    @Test
    fun `promotion writes the sensor change at the pre-soak activation time`() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
        startPresoak()
        feed(10)

        assertThat(plugin.promoteStagingToProduction()).isEqualTo(PromotionResult.Ok)

        verify(persistenceLayer, timeout(SLOW_INSERT_MS)).insertCgmSourceData(
            eq(Sources.Libre3Native), eq(emptyList()), eq(emptyList()), eq(stagedActivatedAtMs),
        )
    }

    @Test
    fun `promotion takes the staged sensor over into the production file`() = runTest {
        startPresoak()
        feed(10)

        assertThat(plugin.promoteStagingToProduction()).isEqualTo(PromotionResult.Ok)

        val production = Libre3SensorStore(context, null).loadIdentity()!!
        assertThat(production.serialNumber).isEqualTo(staged.serialNumber)
        assertThat(production.bleAddress).isEqualTo(staged.bleAddress)
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadIdentity()).isNull()
    }

    @Test
    fun `promotion is refused when no staging sensor is present and writes nothing`() = runTest {
        val before = seedRunningSensor()

        val early = plugin.promoteStagingToProduction(allowEarly = true)
        val normal = plugin.promoteStagingToProduction(allowEarly = false)

        assertThat(early).isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_ABSENT))
        assertThat(normal).isEqualTo(early)
        assertNothingIrreversible(before)
    }

    @Test
    fun `promotion is refused when the staged identity is missing and writes nothing`() = runTest {
        val before = seedRunningSensor()
        startPresoak()
        Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).clear()

        assertThat(plugin.promoteStagingToProduction(allowEarly = true))
            .isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_ABSENT))

        assertNothingIrreversible(before)
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.WARMUP)
    }

    @Test
    fun `promotion is refused when the staged identity cannot be read and writes nothing`() = runTest {
        val before = seedRunningSensor()
        startPresoak()
        stagingGate.throwOnSerial = true

        assertThat(plugin.promoteStagingToProduction(allowEarly = false))
            .isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_ABSENT))

        assertNothingIrreversible(before)
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadSlotPresent()).isTrue()
    }

    @Test
    fun `promotion is refused when the session keys cannot be read and writes nothing`() = runTest {
        val before = seedRunningSensor()
        startPresoak()
        stagingGate.throwOnSessionKeys = true

        assertThat(plugin.promoteStagingToProduction())
            .isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_ABSENT))

        assertNothingIrreversible(before)
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.WARMUP)
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadIdentity()!!.serialNumber)
            .isEqualTo(staged.serialNumber)
    }

    @Test
    fun `promotion is refused when the production file refuses the commit and writes nothing`() = runTest {
        val underlying = SharedPreferencesMock()
        whenever(context.getSharedPreferences(PRODUCTION_PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(underlying)
        val seeded = Libre3SensorStore(context, null)
        assertThat(seeded.saveIdentityAndWait(running)).isTrue()
        seeded.saveLastLifeCount(9_000)
        seeded.saveSensorChangeLoggedSerial(running.serialNumber)
        val before = HashMap(underlying.all)
        whenever(context.getSharedPreferences(PRODUCTION_PREFS_NAME, Context.MODE_PRIVATE))
            .thenReturn(CommitRefusingPreferences(underlying))
        startPresoak()

        val early = plugin.promoteStagingToProduction(allowEarly = true)
        val normal = plugin.promoteStagingToProduction(allowEarly = false)

        assertThat(early).isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_ABSENT))
        assertThat(normal).isEqualTo(early)
        runBlocking {
            verify(activeCalibration, never()).ignoreEntriesBefore(any())
            verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
        }
        assertThat(underlying.all).isEqualTo(before)
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.WARMUP)
    }

    @Test
    fun `a thrown cutoff keeps the swap and reports that the bound failed`() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
        whenever(rh.gs(app.aaps.plugins.source.R.string.libre3_presoak_promote_bound_failed))
            .thenReturn(BOUND_FAILED_TEXT)
        val cutoff = ThrowingCutoff()
        val logger = RecordingLogger()
        val alerts = mutableListOf<String>()
        plugin = newPlugin(logger = logger, calibration = cutoff)
        plugin.promotionAlerter = { alerts += it }
        startPresoak()
        val presoakDriver = Libre3CgmDrivers.stagingOrNull()

        assertThat(plugin.promoteStagingToProduction()).isEqualTo(PromotionResult.OkBoundFailed)

        assertThat(alerts).contains(BOUND_FAILED_TEXT)
        assertThat(cutoff.calls).isEqualTo(1)
        assertThat(cutoff.storedTimestamp).isNull()
        assertThat(logger.errors).contains(BOUND_FAILED_TEXT)
        assertThat(Libre3SensorStore(context, null).loadIdentity()!!.serialNumber).isEqualTo(staged.serialNumber)
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadIdentity()).isNull()
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
        // The pre-soak instance is the production driver now.
        assertThat(Libre3CgmDrivers.default()).isSameInstanceAs(presoakDriver)
        verify(persistenceLayer, timeout(SLOW_INSERT_MS)).insertCgmSourceData(
            eq(Sources.Libre3Native), eq(emptyList()), eq(emptyList()), eq(stagedActivatedAtMs),
        )
    }

    @Test
    fun `a failure after the cutoff leaves the swap done and does not return Ok`() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
        whenever(rh.gs(app.aaps.plugins.source.R.string.libre3_presoak_promote_check_state)).thenReturn("check")
        val cutoff = StoringCutoff()
        plugin = newPlugin(logger = PromoteDoneFailsLogger(), calibration = cutoff)
        plugin.promotionAlerter = {}
        startPresoak()
        val presoakDriver = Libre3CgmDrivers.stagingOrNull()

        val result = plugin.promoteStagingToProduction()

        assertThat(result).isEqualTo(PromotionResult.OkFollowUpFailed(productionIdentityPresent = true))
        assertThat(Libre3SensorStore(context, null).loadIdentity()).isNotNull()
        assertThat(cutoff.storedTimestamp).isNotNull()
        assertThat(Libre3SensorStore(context, null).loadIdentity()!!.serialNumber).isEqualTo(staged.serialNumber)
        assertThat(Libre3SensorStore(context, Libre3CgmDrivers.STAGING_NAMESPACE).loadIdentity()).isNull()
        assertThat(plugin.stagingState.value).isEqualTo(StagingState.ABSENT)
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNull()
        // The pre-soak instance is the production driver now.
        assertThat(Libre3CgmDrivers.default()).isSameInstanceAs(presoakDriver)
        verify(persistenceLayer, timeout(SLOW_INSERT_MS)).insertCgmSourceData(
            eq(Sources.Libre3Native), eq(emptyList()), eq(emptyList()), eq(stagedActivatedAtMs),
        )
    }

    @Test
    fun `a thrown sensor-change insert during promotion is logged and the result matches the store`() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
        runBlocking {
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenThrow(IllegalStateException("insert"))
        }
        val logger = RecordingLogger()
        plugin = newPlugin(logger = logger)
        plugin.promotionAlerter = {}
        startPresoak()

        val result = plugin.promoteStagingToProduction()

        assertThat(result).isEqualTo(PromotionResult.Ok)
        assertThat(Libre3SensorStore(context, null).loadIdentity()!!.serialNumber).isEqualTo(staged.serialNumber)
        val deadline = System.currentTimeMillis() + SLOW_INSERT_MS
        while (logger.errors.none { it.contains("background work failed") } && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertThat(logger.errors.single()).contains("${Libre3LogMarkers.ERROR}: background work failed, insert")
    }

    @Test
    fun `a thrown bound and a thrown follow-up keep the swap and report the bound`() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
        whenever(rh.gs(app.aaps.plugins.source.R.string.libre3_presoak_promote_bound_failed)).thenReturn(BOUND_FAILED_TEXT)
        whenever(rh.gs(app.aaps.plugins.source.R.string.libre3_presoak_promote_check_state)).thenReturn("check")
        val logger = RecordingLogger(PromoteDoneFailsLogger())
        val alerts = mutableListOf<String>()
        plugin = newPlugin(logger = logger, calibration = ThrowingCutoff())
        plugin.promotionAlerter = { alerts += it }
        startPresoak()

        val result = plugin.promoteStagingToProduction()

        assertThat(result).isEqualTo(PromotionResult.OkBoundFailed)
        assertThat(Libre3SensorStore(context, null).loadIdentity()!!.serialNumber).isEqualTo(staged.serialNumber)
        assertThat(logger.errors).containsAtLeast(BOUND_FAILED_TEXT, "check")
        assertThat(alerts).containsAtLeast(BOUND_FAILED_TEXT, "check")
    }

    @Test
    fun `a follow-up failure with no readable production identity is not a success`() = runTest {
        whenever(rh.gs(app.aaps.plugins.source.R.string.libre3_presoak_promote_follow_up_no_identity)).thenReturn("missing")
        val gated = GatedPreferences(productionPrefs)
        whenever(context.getSharedPreferences(PRODUCTION_PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(gated)
        val logger = PromoteDoneFailsLogger()
        plugin = newPlugin(
            logger = object : AAPSLogger by logger {
                override fun info(tag: LTag, message: String) {
                    if (message.contains("promote done")) gated.throwOnSerial = true
                    logger.info(tag, message)
                }
            },
        )
        plugin.promotionAlerter = {}
        startPresoak()

        val result = plugin.promoteStagingToProduction()

        assertThat(result).isEqualTo(PromotionResult.OkFollowUpFailed(productionIdentityPresent = false))
        assertThat(gated.throwOnSerial).isTrue()
    }

    private class GatedPreferences(
        private val real: SharedPreferences,
    ) : SharedPreferences by real {

        var throwOnSerial: Boolean = false
        var throwOnSessionKeys: Boolean = false

        override fun getString(key: String?, defValue: String?): String? {
            if (throwOnSerial && key == "serial") throw IllegalStateException("identity")
            if (throwOnSessionKeys && key == "phase5_raw_key") throw IllegalStateException("session keys")
            return real.getString(key, defValue)
        }
    }

    private class CommitRefusingPreferences(
        private val real: SharedPreferences,
    ) : SharedPreferences by real {

        override fun edit(): SharedPreferences.Editor = RefusingEditor()

        private class RefusingEditor : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = this
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor = this
            override fun clear(): SharedPreferences.Editor = this
            override fun commit(): Boolean = false
            override fun apply() = Unit
        }
    }

    private class ThrowingCutoff : Calibration {
        var calls: Int = 0
        var storedTimestamp: Long? = null

        override suspend fun ignoreEntriesBefore(timestamp: Long) {
            calls++
            throw IllegalStateException("cutoff")
        }

        override suspend fun calibrate(
            data: MutableList<InMemoryGlucoseValue>,
            context: CalibrationContext,
        ): MutableList<InMemoryGlucoseValue> = data

        override suspend fun addEntry(bgMgdl: Double, timestamp: Long): AddEntryResult = AddEntryResult.Accepted

        override suspend fun checkPreconditions(): AddEntryResult = AddEntryResult.Accepted

    }

    private class StoringCutoff : Calibration {
        var storedTimestamp: Long? = null

        override suspend fun ignoreEntriesBefore(timestamp: Long) {
            storedTimestamp = timestamp
        }

        override suspend fun calibrate(
            data: MutableList<InMemoryGlucoseValue>,
            context: CalibrationContext,
        ): MutableList<InMemoryGlucoseValue> = data

        override suspend fun addEntry(bgMgdl: Double, timestamp: Long): AddEntryResult = AddEntryResult.Accepted

        override suspend fun checkPreconditions(): AddEntryResult = AddEntryResult.Accepted

    }

    private class RecordingLogger(
        private val delegate: AAPSLogger = AAPSLoggerTest(),
    ) : AAPSLogger by delegate {

        val errors = mutableListOf<String>()

        override fun error(tag: LTag, message: String, throwable: Throwable) {
            errors += message
            delegate.error(tag, message, throwable)
        }
    }

    private class PromoteDoneFailsLogger(
        private val delegate: AAPSLogger = AAPSLoggerTest(),
    ) : AAPSLogger by delegate {

        override fun info(tag: LTag, message: String) {
            if (message.contains("promote done")) throw IllegalStateException("log failed")
            delegate.info(tag, message)
        }
    }

    companion object {
        private const val PRODUCTION_PREFS_NAME = "libre3_sensor_store"
        private const val STAGING_PREFS_NAME = "libre3_sensor_store_staging"
        private const val SLOW_INSERT_MS = 5_000L
        private const val BOUND_FAILED_TEXT =
            "The sensor is promoted, but the old calibration entries could not be ignored. " +
                "Check a fingerstick before you trust the loop, and calibrate again if those old entries still apply."
    }
}
