package app.aaps.plugins.source

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.plugins.source.keys.DexcomOnePlusBooleanKey
import app.aaps.plugins.source.keys.DexcomOnePlusLongKey
import app.aaps.shared.tests.SharedPreferencesMock
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.after
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.reflect.Field

/**
 * The sensor start and its SENSOR_CHANGE, with no settings: the event is always written at the start
 * (no second one next to an existing one), the start is never moved back to an earlier event, only
 * the plugin's own event is moved when the sensor reports its start, and a missing event is put
 * back once per session.
 */
class DexcomOnePlusSensorAgeRepairTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config
    @Mock lateinit var context: Context
    @Mock lateinit var persistenceLayer: PersistenceLayer


    private val notificationManager: NotificationManager = mock()
    private val activePlugin: ActivePlugin = mock()
    private val prefs: SharedPreferences = SharedPreferencesMock()

    private lateinit var plugin: DexcomOnePlusPlugin

    @BeforeEach
    fun setup() = runTest {
        rxBus = mock()
        setBuildField("MANUFACTURER", "Google")
        setBuildField("MODEL", "Pixel")
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSharedPreferences(PRODUCTION_PREFS, Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(persistenceLayer.insertCgmSourceData(any(), any(), any(), anyOrNull())).thenAnswer { invocation ->
            val result = PersistenceLayer.TransactionResult<GV>()
            val stamp = invocation.arguments[3] as Long?
            if (stamp != null) {
                result.sensorInsertionsInserted.add(sensorChange(stamp))
            }
            result
        }
        whenever(persistenceLayer.getTherapyEventDataIncludingInvalidFromTime(any(), any()))
            .thenReturn(emptyList())
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(emptyList())
        plugin = DexcomOnePlusPlugin(
            rh, aapsLogger, preferences, config, notificationManager, DexcomOnePlusAndroidHost(context) { error("the log is not read here") }, persistenceLayer,
            mock<DexcomOnePlusWarmupBasalGuard>(), activePlugin, rxBus,
        )
        DexcomOnePlusIngest.reset()
    }

    @Test
    fun `a start writes the sensor change at the start time`() = runTest {
        val auto = System.currentTimeMillis()

        plugin.onSensorSessionStarted(MAC, previousMac = null, startMs = auto)

        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(auto))
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(auto)
        verify(preferences, timeout(2_000L)).put(DexcomOnePlusLongKey.OwnSensorChangeAt, auto)
    }

    /**
     * The bug the anchor had: a sensor replaced less than a day after the previous one was dated at
     * the previous sensor's event. The start is now the start, whatever came before.
     */
    @Test
    fun `a start is never moved back to the previous sensor's change`() = runTest {
        val auto = System.currentTimeMillis()
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(auto - 10 * HOUR_MS))

        plugin.onSensorSessionStarted(MAC, previousMac = null, startMs = auto)

        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(auto))
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(auto)
    }

    @Test
    fun `a sensor change already logged near the start is kept and not doubled`() = runTest {
        val auto = System.currentTimeMillis()
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(listOf(sensorChange(auto - 10 * MINUTE_MS)))

        plugin.onSensorSessionStarted(MAC, previousMac = null, startMs = auto)

        verify(persistenceLayer, after(500L).never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(auto)
    }

    @Test
    fun `the first reading keeps the reading time even with an earlier sensor change`() = runTest {
        val auto = System.currentTimeMillis()
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(auto - 2 * HOUR_MS))
        val sample = OnePlusGlucoseSample(mgdl = 110.0, timestampMs = auto, sequence = 1L)

        ingest(sample)

        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(null))
        assertThat(awaitSessionStart()).isEqualTo(auto)
    }

    @Test
    fun `no sensor change setting is shown or read any more`() {
        val items = plugin.getPreferenceScreenContent().items
        assertThat(items).doesNotContain(BooleanKey.BgSourceCreateSensorChange)
        assertThat(DexcomOnePlusBooleanKey.entries.map { it.key }).containsNoneOf(
            "dexcom_oneplus_repair_missing_sensor_change",
            "dexcom_oneplus_correct_sensor_start",
            "dexcom_oneplus_anchor_session_to_manual_sensor_change",
        )
    }

    @Test
    fun `the sensor's own start replaces the plugin's own sensor change`() = runTest {
        val old = System.currentTimeMillis() - 2 * HOUR_MS
        val reported = old - 30 * MINUTE_MS
        whenever(preferences.get(DexcomOnePlusLongKey.OwnSensorChangeAt)).thenReturn(old)
        val own = sensorChange(old)
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(listOf(own)).thenReturn(emptyList())

        plugin.moveToReportedStart(old, reported)

        verify(persistenceLayer).invalidateTherapyEvent(eq(own.id), any(), any(), anyOrNull(), any())
        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(reported))
    }

    /** A sensor change logged by hand or synced from Nightscout is never removed by the plugin. */
    @Test
    fun `the sensor's own start never removes a sensor change the plugin did not write`() = runTest {
        val old = System.currentTimeMillis() - 2 * HOUR_MS
        val reported = old - 30 * MINUTE_MS
        // The plugin's own event was at another time: the one near the old start is the user's.
        whenever(preferences.get(DexcomOnePlusLongKey.OwnSensorChangeAt)).thenReturn(old - 5 * MINUTE_MS)
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(listOf(sensorChange(old)))

        plugin.moveToReportedStart(old, reported)

        verify(persistenceLayer, never()).invalidateTherapyEvent(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `a sensor whose therapy event is missing gets its age put back once`() = runTest {
        val startedAt = System.currentTimeMillis() - 26 * HOUR_MS
        OnePlusSensorStore(context).startSessionForSensor("AA:BB:CC:DD:EE:10", startedAt, null)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE))
            .thenReturn(sensorChange(System.currentTimeMillis() - 13 * 24 * HOUR_MS))

        refreshLifecycle()

        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(startedAt))
        verify(rxBus, timeout(2_000L)).send(any<EventRefreshOverview>())

        clearInvocations(persistenceLayer, rxBus)
        refreshLifecycle()

        verify(persistenceLayer, after(500L).never())
            .insertCgmSourceData(any(), any(), any(), anyOrNull())
        verify(rxBus, never()).send(any<EventRefreshOverview>())
    }

    @Test
    fun `an age that already matches is left alone`() = runTest {
        val startedAt = System.currentTimeMillis() - 26 * HOUR_MS
        OnePlusSensorStore(context).startSessionForSensor("AA:BB:CC:DD:EE:12", startedAt, null)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(startedAt))

        refreshLifecycle()

        verify(persistenceLayer, after(500L).never())
            .insertCgmSourceData(any(), any(), any(), anyOrNull())
        verify(rxBus, never()).send(any<EventRefreshOverview>())
    }

    private fun setBuildField(name: String, value: String) {
        val field = android.os.Build::class.java.getDeclaredField(name)
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val base = unsafeClass.getMethod("staticFieldBase", Field::class.java).invoke(unsafe, field)
        val offset = unsafeClass.getMethod("staticFieldOffset", Field::class.java).invoke(unsafe, field) as Long
        unsafeClass.getMethod("putObject", Any::class.java, java.lang.Long.TYPE, Any::class.java)
            .invoke(unsafe, base, offset, value)
    }

    private fun refreshLifecycle() {
        DexcomOnePlusPlugin::class.java.getDeclaredMethod("refreshProductionLifecycle")
            .apply { isAccessible = true }
            .invoke(plugin)
    }

    private fun awaitSessionStart(): Long {
        val deadline = System.currentTimeMillis() + 2_000L
        var stored = prefs.getLong(KEY_SESSION_START, 0L)
        while (stored == 0L && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            stored = prefs.getLong(KEY_SESSION_START, 0L)
        }
        return stored
    }

    private fun ingest(sample: OnePlusGlucoseSample) {
        val store = OnePlusSensorStore(context)
        DexcomOnePlusPlugin::class.java.getDeclaredMethod(
            "ingestToLoop",
            OnePlusGlucoseSample::class.java,
            OnePlusSensorStore::class.java,
        ).apply { isAccessible = true }.invoke(plugin, sample, store)
    }

    private fun sensorChange(timestamp: Long): TE = TE(
        id = 11L,
        timestamp = timestamp,
        type = TE.Type.SENSOR_CHANGE,
        glucoseUnit = GlucoseUnit.MGDL,
    )

    companion object {
        private const val HOUR_MS = 60L * 60L * 1000L
        private const val MINUTE_MS = 60L * 1000L
        private const val MAC = "AA:BB:CC:DD:EE:01"
        private const val PRODUCTION_PREFS = "dexcom_oneplus_sensor"
        private const val KEY_SESSION_START = "session_start_ms"
    }
}
