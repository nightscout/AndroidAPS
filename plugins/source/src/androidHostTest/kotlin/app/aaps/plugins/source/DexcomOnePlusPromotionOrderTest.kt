package app.aaps.plugins.source

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.calibration.Calibration
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
import app.aaps.plugins.dexcomoneplus.OnePlusAndroidPlatform
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriverReal
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDrivers
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorIdentity
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.plugins.dexcomoneplus.session.OnePlusMacArbiter
import app.aaps.plugins.source.activities.dexcomOnePlusPromotionMessage
import app.aaps.shared.tests.AAPSLoggerTest
import app.aaps.shared.tests.SharedPreferencesMock
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

/**
 * Promotion order of Dexcom ONE+.
 *
 * Every refusal returns before the irreversible steps. A success runs the guards, then the
 * exchange, then `logSensorChange`, then `ignoreEntriesBefore`. A throw from
 * `ignoreEntriesBefore` does not undo the exchange, and the result is not Ok.
 */
class DexcomOnePlusPromotionOrderTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config
    @Mock lateinit var context: Context
    @Mock lateinit var persistenceLayer: PersistenceLayer


    private val notificationManager: NotificationManager = mock()
    private val activeCalibration: Calibration = mock()
    private val activePlugin: ActivePlugin = mock<ActivePlugin>().also {
        whenever(it.activeCalibration).thenReturn(activeCalibration)
    }
    private val logger = RecordingLogger()
    private val alerts = mutableListOf<String>()

    private val productionPrefs: SharedPreferences = SharedPreferencesMock()
    private val stagingPrefs: SharedPreferences = SharedPreferencesMock()

    private lateinit var plugin: DexcomOnePlusPlugin

    @BeforeEach
    fun setup() = runTest {
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSharedPreferences(PRODUCTION_PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(productionPrefs)
        whenever(context.getSharedPreferences(STAGING_PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(stagingPrefs)
        whenever(persistenceLayer.insertCgmSourceData(any(), any(), any(), anyOrNull()))
            .thenReturn(PersistenceLayer.TransactionResult())
        plugin = DexcomOnePlusPlugin(
            rh, logger, preferences, config, notificationManager, DexcomOnePlusAndroidHost(context), persistenceLayer,
            activePlugin, rxBus,
        )
        plugin.promotionAlerter = { alerts += it }
    }

    @AfterEach
    fun tearDownDrivers() {
        OnePlusCgmDrivers.shutdownAll()
        OnePlusMacArbiter.reset()
    }

    @Test
    fun `absent staging writes nothing irreversible`() = runTest {
        val stagingBefore = stagingPrefs.all.toMap()
        val result = plugin.promoteStagingToProduction(allowEarly = true)

        assertThat(result).isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_ABSENT))
        assertNothingIrreversible(stagingBefore)
    }

    @Test
    fun `fewer than six readings writes nothing irreversible`() = runTest {
        setPrivate("stagingPresent", true)
        setPrivate("stagingValidEgvCount", 5)
        val stagingBefore = stagingPrefs.all.toMap()

        val result = plugin.promoteStagingToProduction(allowEarly = true)

        assertThat(result).isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_NO_VALID_GLUCOSE))
        assertNothingIrreversible(stagingBefore)
    }

    @Test
    fun `a missing soak clock writes nothing irreversible`() = runTest {
        setPrivate("stagingPresent", true)
        setPrivate("stagingValidEgvCount", 6)
        val stagingBefore = stagingPrefs.all.toMap()

        val result = plugin.promoteStagingToProduction(allowEarly = false)

        assertThat(result).isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_NOT_SETTLED))
        assertNothingIrreversible(stagingBefore)
    }

    @Test
    fun `an early promotion without a recent reading writes nothing irreversible`() = runTest {
        readyStaging(mac = "AA:BB:CC:DD:EE:21", ageMs = 60_000L)
        setPrivate("stagingLastValueAtMs", System.currentTimeMillis() - 30L * 60L * 1000L)
        val stagingBefore = stagingPrefs.all.toMap()

        val result = plugin.promoteStagingToProduction(allowEarly = true)

        assertThat(result).isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_NO_RECENT_GLUCOSE))
        assertNothingIrreversible(stagingBefore)
    }

    @Test
    fun `a soak shorter than twelve hours writes nothing irreversible`() = runTest {
        readyStaging(mac = "AA:BB:CC:DD:EE:22", ageMs = 60_000L)
        setStagingState(StagingState.READY)
        val stagingBefore = stagingPrefs.all.toMap()

        val result = plugin.promoteStagingToProduction(allowEarly = false)

        assertThat(result).isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_NOT_SETTLED))
        assertNothingIrreversible(stagingBefore)
    }

    @Test
    fun `a long soak that is not READY writes nothing irreversible`() = runTest {
        readyStaging(mac = "AA:BB:CC:DD:EE:23", ageMs = 13L * 60L * 60L * 1000L)
        setStagingState(StagingState.WARMUP)
        val stagingBefore = stagingPrefs.all.toMap()

        val result = plugin.promoteStagingToProduction(allowEarly = false)

        assertThat(result).isEqualTo(PromotionResult.Rejected(PromotionRejectReason.STAGING_NOT_SETTLED))
        assertNothingIrreversible(stagingBefore)
    }

    @Test
    fun `the bound runs after the exchange and before select`() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
        val startMs = System.currentTimeMillis() - 60_000L
        readyStaging(mac = "AA:BB:CC:DD:EE:24", ageMs = 60_000L, startMs = startMs)
        val soak = OnePlusCgmDrivers.staging()
        var productionWasSoakAtBound = false
        var stagingClearedAtBound = false
        var identityAdoptedAtBound = false
        whenever(activeCalibration.ignoreEntriesBefore(any())).thenAnswer {
            productionWasSoakAtBound = OnePlusCgmDrivers.realProduction() === soak
            stagingClearedAtBound = stagingPrefs.getString(KEY_PIN, null) == null
            identityAdoptedAtBound = productionPrefs.getString(KEY_PIN, null) == "1234"
            null
        }

        val result = plugin.promoteStagingToProduction(allowEarly = true)

        assertThat(result).isEqualTo(PromotionResult.Ok)
        // At the bound the exchange is done.
        assertThat(identityAdoptedAtBound).isTrue()
        assertThat(stagingClearedAtBound).isTrue()
        assertThat(productionWasSoakAtBound).isTrue()
        val order = inOrder(preferences, activeCalibration)
        order.verify(preferences).get(BooleanKey.BgSourceCreateSensorChange)
        order.verify(activeCalibration).ignoreEntriesBefore(any())
        assertThat(OnePlusCgmDrivers.default()).isSameInstanceAs(soak)
        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(startMs))
    }

    @Test
    fun `a thrown bound keeps the exchange and is not a silent success`() = runTest {
        // The throw does not undo the exchange, and the result is not Ok. The user is told the
        // sensor was promoted and what to do.
        readyStaging(mac = "AA:BB:CC:DD:EE:25", ageMs = 60_000L)
        val soak = OnePlusCgmDrivers.staging()
        whenever(rh.gs(SourceStrings.dexcom_oneplus_staging_promote_bound_failed)).thenReturn(BOUND_FAILED_TEXT)
        whenever(activeCalibration.ignoreEntriesBefore(any())).thenThrow(IllegalStateException("bound failed"))

        val result = plugin.promoteStagingToProduction(allowEarly = true)

        assertThat(result).isEqualTo(PromotionResult.OkBoundFailed)
        verify(activeCalibration).ignoreEntriesBefore(any())
        assertThat(productionPrefs.getString(KEY_PIN, null)).isEqualTo("1234")
        assertThat(stagingPrefs.getString(KEY_PIN, null)).isNull()
        assertThat(OnePlusCgmDrivers.default()).isSameInstanceAs(soak)
        assertThat(alerts).containsExactly(BOUND_FAILED_TEXT)
        assertThat(logger.errors).contains(BOUND_FAILED_TEXT)
    }

    @Test
    fun `a failure after the bound keeps the exchange and is not a complete success`() = runTest {
        // load() / resume throws after the bound. The exchange stays. The result reads the
        // production identity instead of announcing Ok.
        readyStaging(mac = "AA:BB:CC:DD:EE:26", ageMs = 60_000L)
        val soak = OnePlusCgmDrivers.staging()
        whenever(rh.gs(SourceStrings.dexcom_oneplus_staging_promote_follow_up_failed)).thenReturn(FOLLOW_UP_TEXT)
        val steps = mutableListOf<String>()
        whenever(activeCalibration.ignoreEntriesBefore(any())).thenAnswer {
            steps.add("bound")
            null
        }
        val throwingStore = mock<OnePlusSensorStore>()
        whenever(throwingStore.load()).thenAnswer {
            steps.add("resume")
            throw IllegalStateException("resume failed after the bound")
        }
        OnePlusCgmDriverReal::class.java.getDeclaredField("sensorStore").apply {
            isAccessible = true
            set(soak, throwingStore)
        }

        val result = plugin.promoteStagingToProduction(allowEarly = true)

        assertThat(result).isEqualTo(PromotionResult.OkFollowUpFailed(productionIdentityPresent = true))
        assertThat(steps).containsExactly("bound", "resume").inOrder()
        assertThat(productionPrefs.getString(KEY_PIN, null)).isEqualTo("1234")
        assertThat(stagingPrefs.getString(KEY_PIN, null)).isNull()
        assertThat(OnePlusCgmDrivers.default()).isSameInstanceAs(soak)
        assertThat(alerts).containsExactly(FOLLOW_UP_TEXT)
        assertThat(logger.errors).contains(FOLLOW_UP_TEXT)
    }

    @Test
    fun `a thrown bound wins when the follow-up also fails`() = runTest {
        readyStaging(mac = "AA:BB:CC:DD:EE:29", ageMs = 60_000L)
        val soak = OnePlusCgmDrivers.staging()
        whenever(rh.gs(SourceStrings.dexcom_oneplus_staging_promote_bound_failed)).thenReturn(BOUND_FAILED_TEXT)
        whenever(rh.gs(SourceStrings.dexcom_oneplus_staging_promote_follow_up_failed)).thenReturn(FOLLOW_UP_TEXT)
        whenever(activeCalibration.ignoreEntriesBefore(any())).thenThrow(IllegalStateException("bound failed"))
        val throwingStore = mock<OnePlusSensorStore>()
        whenever(throwingStore.load()).thenThrow(IllegalStateException("resume failed after the bound"))
        OnePlusCgmDriverReal::class.java.getDeclaredField("sensorStore").apply {
            isAccessible = true
            set(soak, throwingStore)
        }

        val result = plugin.promoteStagingToProduction(allowEarly = true)

        assertThat(result).isEqualTo(PromotionResult.OkBoundFailed)
        assertThat(productionPrefs.getString(KEY_PIN, null)).isEqualTo("1234")
        assertThat(stagingPrefs.getString(KEY_PIN, null)).isNull()
        assertThat(OnePlusCgmDrivers.default()).isSameInstanceAs(soak)
    }

    @Test
    fun `a follow-up failure with no production identity is not a complete success`() = runTest {
        readyStaging(mac = "AA:BB:CC:DD:EE:2A", ageMs = 60_000L)
        val soak = OnePlusCgmDrivers.staging()
        whenever(rh.gs(SourceStrings.dexcom_oneplus_staging_promote_follow_up_failed_no_identity))
            .thenReturn(FOLLOW_UP_NO_IDENTITY_TEXT)
        val throwingStore = mock<OnePlusSensorStore>()
        whenever(throwingStore.load()).thenAnswer {
            productionPrefs.edit().remove(KEY_PIN).commit()
            throw IllegalStateException("resume failed after the bound")
        }
        OnePlusCgmDriverReal::class.java.getDeclaredField("sensorStore").apply {
            isAccessible = true
            set(soak, throwingStore)
        }

        val result = plugin.promoteStagingToProduction(allowEarly = true)

        assertThat(result).isEqualTo(PromotionResult.OkFollowUpFailed(productionIdentityPresent = false))
        assertThat(productionPrefs.getString(KEY_PIN, null)).isNull()
        assertThat(OnePlusCgmDrivers.default()).isSameInstanceAs(soak)
        assertThat(alerts).containsExactly(FOLLOW_UP_NO_IDENTITY_TEXT)
    }

    @Test
    fun `promotion texts distinguish refusal, a failed bound, and a later failure`() {
        val english = readResource("src/androidMain/res/values/strings.xml")
        assertThat(english).contains(
            "Sensor promoted, but older calibration entries could not be ignored. " +
                "Remove fingerstick calibrations taken before this switch, then calibrate again if needed.",
        )
        assertThat(
            dexcomOnePlusPromotionMessage(
                result = PromotionResult.Rejected(PromotionRejectReason.STAGING_ABSENT),
                ok = "ok",
                boundFailed = "bound",
                followUpIdentityPresent = "follow",
                followUpIdentityMissing = "missing",
                rejectedAbsent = "absent",
                rejectedNotSettled = "settling",
                rejectedNoGlucose = "glucose",
                rejectedNoRecentGlucose = "recent",
            ),
        ).isEqualTo("absent")
        assertThat(
            dexcomOnePlusPromotionMessage(
                result = PromotionResult.OkBoundFailed,
                ok = "ok",
                boundFailed = "bound",
                followUpIdentityPresent = "follow",
                followUpIdentityMissing = "missing",
                rejectedAbsent = "absent",
                rejectedNotSettled = "settling",
                rejectedNoGlucose = "glucose",
                rejectedNoRecentGlucose = "recent",
            ),
        ).isEqualTo("bound")
        assertThat(
            dexcomOnePlusPromotionMessage(
                result = PromotionResult.OkFollowUpFailed(productionIdentityPresent = true),
                ok = "ok",
                boundFailed = "bound",
                followUpIdentityPresent = "follow",
                followUpIdentityMissing = "missing",
                rejectedAbsent = "absent",
                rejectedNotSettled = "settling",
                rejectedNoGlucose = "glucose",
                rejectedNoRecentGlucose = "recent",
            ),
        ).isEqualTo("follow")
        assertThat(
            dexcomOnePlusPromotionMessage(
                result = PromotionResult.OkFollowUpFailed(productionIdentityPresent = false),
                ok = "ok",
                boundFailed = "bound",
                followUpIdentityPresent = "follow",
                followUpIdentityMissing = "missing",
                rejectedAbsent = "absent",
                rejectedNotSettled = "settling",
                rejectedNoGlucose = "glucose",
                rejectedNoRecentGlucose = "recent",
            ),
        ).isEqualTo("missing")
        assertThat(
            dexcomOnePlusPromotionMessage(
                result = PromotionResult.Ok,
                ok = "ok",
                boundFailed = "bound",
                followUpIdentityPresent = "follow",
                followUpIdentityMissing = "missing",
                rejectedAbsent = "absent",
                rejectedNotSettled = "settling",
                rejectedNoGlucose = "glucose",
                rejectedNoRecentGlucose = "recent",
            ),
        ).isEqualTo("ok")
    }

    @Test
    fun `promotion shows the promoted driver's phase, not the retired one`() = runTest {
        readyStaging(mac = "AA:BB:CC:DD:EE:27", ageMs = 60_000L)
        plugin.onWarmup(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.READY))
        assertThat(plugin.warmup.value.phase).isEqualTo(OnePlusWarmupState.Phase.READY)

        val result = plugin.promoteStagingToProduction(allowEarly = true)

        assertThat(result).isEqualTo(PromotionResult.Ok)
        assertThat(plugin.warmup.value.phase).isEqualTo(OnePlusWarmupState.Phase.IDLE)
    }

    @Test
    fun `after promotion the live link writes its MAC into the production file`() = runTest {
        val mac = "AA:BB:CC:DD:EE:28"
        readyStaging(mac = mac, ageMs = 60_000L)
        val promoted = OnePlusCgmDrivers.staging()
        bindDriverContext(promoted)

        assertThat(plugin.promoteStagingToProduction(allowEarly = true)).isEqualTo(PromotionResult.Ok)

        promoted.onAuthSucceeded(mac, ByteArray(16) { 7 })

        assertThat(productionPrefs.getString(KEY_MAC, null)).isEqualTo(mac)
        assertThat(stagingPrefs.getString(KEY_MAC, null)).isNull()
        assertThat(stagingPrefs.getString(KEY_PIN, null)).isNull()
        assertThat(promoted.sensorStore()?.load()?.identity?.pin).isEqualTo("1234")
    }

    @Test
    fun `the settle countdown is set by a state refresh and cleared by a cancel`() {
        readyStaging(mac = "AA:BB:CC:DD:EE:30", ageMs = HOUR_MS)
        assertThat(plugin.stagingSettleRemainingMs.value).isNull()

        refreshStagingState()

        assertCountdownAboutElevenHours()
        plugin.cancelStaging()
        assertThat(plugin.stagingSettleRemainingMs.value).isNull()
    }

    @Test
    fun `the settle countdown is cleared by a promotion`() = runTest {
        readyStaging(mac = "AA:BB:CC:DD:EE:31", ageMs = HOUR_MS)
        refreshStagingState()
        assertCountdownAboutElevenHours()

        assertThat(plugin.promoteStagingToProduction(allowEarly = true)).isEqualTo(PromotionResult.Ok)

        assertThat(plugin.stagingSettleRemainingMs.value).isNull()
    }

    @Test
    fun `the settle countdown is cleared when a stored pre-soak cannot be resumed`() {
        // A slot is marked present, but it has no MAC, so the driver refuses to resume it.
        // The resume attaches the driver, and that builds a scanner from these two services.
        whenever(context.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(mock<BluetoothManager>())
        whenever(context.getSystemService(Context.POWER_SERVICE)).thenReturn(mock<PowerManager>())
        val stagingStore = OnePlusSensorStore(context, OnePlusCgmDrivers.STAGING_NAMESPACE)
        stagingStore.saveIdentity(OnePlusSensorIdentity(pin = "1234", serial = "SOAK1"))
        stagingStore.saveSlotProgress(present = true, validEgvCount = 6)
        stagingStore.saveSessionStartIfAbsent(System.currentTimeMillis() - HOUR_MS)
        setPrivate("stagingPresent", true)
        refreshStagingState()
        assertCountdownAboutElevenHours()

        val resumed = DexcomOnePlusPlugin::class.java.getDeclaredMethod("resumeStagingSessionIfStored")
            .apply { isAccessible = true }
            .invoke(plugin) as Boolean

        assertThat(resumed).isFalse()
        assertThat(plugin.stagingSettleRemainingMs.value).isNull()
    }

    private fun refreshStagingState() {
        DexcomOnePlusPlugin::class.java.getDeclaredMethod("refreshStagingState")
            .apply { isAccessible = true }
            .invoke(plugin)
    }

    /** The soak started one hour ago, so about eleven of the twelve hours are left. */
    private fun assertCountdownAboutElevenHours() {
        val remaining = plugin.stagingSettleRemainingMs.value
        assertThat(remaining).isNotNull()
        assertThat(remaining!!).isAtMost(11L * HOUR_MS)
        assertThat(remaining).isGreaterThan(11L * HOUR_MS - 60_000L)
    }

    private suspend fun assertNothingIrreversible(stagingBefore: Map<String, *>) {
        verify(activeCalibration, never()).ignoreEntriesBefore(any())
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
        assertThat(productionPrefs.getString(KEY_PIN, null)).isNull()
        assertThat(productionPrefs.getString(KEY_MAC, null)).isNull()
        assertThat(OnePlusCgmDrivers.default()).isNotSameInstanceAs(OnePlusCgmDrivers.staging())
        // getAll() is the live map. The copy taken before promote must still match.
        assertThat(stagingPrefs.all.toMap()).isEqualTo(stagingBefore)
    }

    private fun readyStaging(mac: String, ageMs: Long, startMs: Long = System.currentTimeMillis() - ageMs) {
        val stagingStore = OnePlusSensorStore(context, OnePlusCgmDrivers.STAGING_NAMESPACE)
        stagingStore.saveIdentity(OnePlusSensorIdentity(pin = "1234", serial = "SOAK1"))
        stagingStore.startSessionForSensor(mac, startMs, null)
        setPrivate("stagingPresent", true)
        setPrivate("stagingValidEgvCount", 6)
        setPrivate("stagingLastValueMgdl", 120.0)
        setPrivate("stagingLastValueAtMs", System.currentTimeMillis())
    }

    private fun setStagingState(state: StagingState) {
        @Suppress("UNCHECKED_CAST")
        val flow = DexcomOnePlusPlugin::class.java.getDeclaredField("_stagingState")
            .apply { isAccessible = true }
            .get(plugin) as MutableStateFlow<StagingState>
        flow.value = state
    }

    private fun readResource(relative: String): String {
        val file = listOf(File(relative), File("plugins/source/$relative")).firstOrNull { it.isFile }
            ?: error("missing $relative from ${File(".").absolutePath}")
        return file.readText()
    }

    private class RecordingLogger : AAPSLogger by AAPSLoggerTest() {
        val errors = mutableListOf<String>()
        override fun error(tag: LTag, message: String, throwable: Throwable) {
            errors += message
        }
    }

    private fun setPrivate(name: String, value: Any?) {
        DexcomOnePlusPlugin::class.java.getDeclaredField(name).apply { isAccessible = true }.set(plugin, value)
    }

    /** Platform and store, without `attach`: that call reads `Build.MANUFACTURER`. */
    private fun bindDriverContext(driver: OnePlusCgmDriverReal) {
        OnePlusCgmDriverReal::class.java.getDeclaredField("platform")
            .apply { isAccessible = true }.set(driver, OnePlusAndroidPlatform(context))
        OnePlusCgmDriverReal::class.java.getDeclaredField("sensorStore")
            .apply { isAccessible = true }
            .set(driver, OnePlusSensorStore(context, OnePlusCgmDrivers.STAGING_NAMESPACE))
    }

    companion object {
        private const val PRODUCTION_PREFS_NAME = "dexcom_oneplus_sensor"
        private const val STAGING_PREFS_NAME = "dexcom_oneplus_sensor_staging"
        private const val KEY_PIN = "pin"
        private const val KEY_MAC = "last_mac"
        private const val HOUR_MS = 60L * 60L * 1000L
        private const val BOUND_FAILED_TEXT =
            "Sensor promoted, but older calibration entries could not be ignored. " +
                "Remove fingerstick calibrations taken before this switch, then calibrate again if needed."
        private const val FOLLOW_UP_TEXT =
            "Sensor promoted, but reconnecting it failed. The production sensor is the one just promoted."
        private const val FOLLOW_UP_NO_IDENTITY_TEXT =
            "A step after the exchange failed, and the production sensor identity is missing."
    }
}
