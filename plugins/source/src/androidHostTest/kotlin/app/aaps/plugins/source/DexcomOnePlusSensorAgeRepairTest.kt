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
 * Age anchor call sites and the once-per-session repair.
 *
 * The repair key is a user setting, on by default. The tests set it explicitly: with it off a
 * refresh writes nothing, with it on the guards below still apply.
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
        plugin = DexcomOnePlusPlugin(
            rh, aapsLogger, preferences, config, notificationManager, DexcomOnePlusAndroidHost(context), persistenceLayer,
            mock<DexcomOnePlusWarmupBasalGuard>(), activePlugin, rxBus,
        )
        DexcomOnePlusIngest.reset()
    }

    @Test
    fun `a start with no sensor change keeps the auto time`() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(null)
        val auto = System.currentTimeMillis()

        plugin.onSensorSessionStarted(MAC, previousMac = null, startMs = auto)

        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(auto))
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(auto)
    }

    @Test
    fun `a start prefers a manual sensor change from the last day`() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
        whenever(preferences.get(DexcomOnePlusBooleanKey.AnchorSessionToManualSensorChange)).thenReturn(true)
        val auto = System.currentTimeMillis()
        val manual = auto - 3 * HOUR_MS
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(manual))

        plugin.onSensorSessionStarted(MAC, previousMac = null, startMs = auto)

        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(manual))
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(manual)
    }

    @Test
    fun `a start ignores a sensor change that is later or older than a day`() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
        val auto = System.currentTimeMillis()
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE))
            .thenReturn(sensorChange(auto + HOUR_MS))

        plugin.onSensorSessionStarted("AA:BB:CC:DD:EE:02", previousMac = null, startMs = auto)

        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(auto))
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(auto)

        val stale = auto - 25 * HOUR_MS
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(stale))
        plugin.onSensorSessionStarted("AA:BB:CC:DD:EE:03", previousMac = null, startMs = auto + 1L)

        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(auto + 1L))
    }

    @Test
    fun `the first reading uses the manual sensor change as the session start`() = runTest {
        whenever(preferences.get(DexcomOnePlusBooleanKey.AnchorSessionToManualSensorChange)).thenReturn(true)
        val auto = System.currentTimeMillis()
        val manual = auto - 2 * HOUR_MS
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(manual))
        val sample = OnePlusGlucoseSample(mgdl = 110.0, timestampMs = auto, sequence = 1L)

        ingest(sample)

        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(null))
        assertThat(awaitSessionStart()).isEqualTo(manual)
    }

    @Test
    fun `the manual anchor switch defaults to on is a user setting and is not exported`() {
        val key = DexcomOnePlusBooleanKey.AnchorSessionToManualSensorChange
        assertThat(key.defaultValue).isTrue()
        assertThat(key.engineeringModeOnly).isFalse()
        assertThat(key.exportable).isFalse()
    }

    @Test
    fun `the three sensor change settings are on by default and shown to every user`() {
        val keys = listOf(
            DexcomOnePlusBooleanKey.RepairMissingSensorChange,
            DexcomOnePlusBooleanKey.CorrectSensorStart,
            DexcomOnePlusBooleanKey.AnchorSessionToManualSensorChange,
        )
        for (key in keys) {
            assertThat(key.defaultValue).isTrue()
            assertThat(key.engineeringModeOnly).isFalse()
        }
        assertThat(plugin.getPreferenceScreenContent().items).containsAtLeastElementsIn(keys)
    }

    @Test
    fun `a start with the anchor switch off does not roll the date back`() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
        val auto = System.currentTimeMillis()
        val manual = auto - 3 * HOUR_MS
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(manual))

        plugin.onSensorSessionStarted("AA:BB:CC:DD:EE:21", previousMac = null, startMs = auto)

        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(auto))
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(auto)
    }

    @Test
    fun `the first reading with the anchor switch off keeps the reading time`() = runTest {
        val auto = System.currentTimeMillis()
        val manual = auto - 2 * HOUR_MS
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(manual))
        val sample = OnePlusGlucoseSample(mgdl = 110.0, timestampMs = auto, sequence = 7L)

        ingest(sample)

        verify(persistenceLayer, timeout(2_000L)).insertCgmSourceData(any(), any(), any(), eq(null))
        assertThat(awaitSessionStart()).isEqualTo(auto)
    }

    @Test
    fun `repair switched off writes nothing`() = runTest {
        assertThat(DexcomOnePlusBooleanKey.RepairMissingSensorChange.defaultValue).isTrue()
        // Stubbed explicitly: the point is the switch-off path, not the Mockito default.
        whenever(preferences.get(DexcomOnePlusBooleanKey.RepairMissingSensorChange)).thenReturn(false)
        // Create-sensor-change stays on, as in the tests below. Without this stub Mockito
        // returns false and the second guard would refuse the write even if the repair switch
        // were gone, so the test would pass for the wrong reason.
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
        val startedAt = System.currentTimeMillis() - 26 * HOUR_MS
        OnePlusSensorStore(context).startSessionForSensor(MAC, startedAt, null)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE))
            .thenReturn(sensorChange(System.currentTimeMillis() - 13 * 24 * HOUR_MS))

        refreshLifecycle()

        verify(persistenceLayer, after(500L).never())
            .insertCgmSourceData(any(), any(), any(), anyOrNull())
        verify(rxBus, never()).send(any<EventRefreshOverview>())
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(startedAt)
    }

    @Test
    fun `repair off for create sensor change writes nothing`() = runTest {
        whenever(preferences.get(DexcomOnePlusBooleanKey.RepairMissingSensorChange)).thenReturn(true)
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(false)
        val startedAt = System.currentTimeMillis() - 26 * HOUR_MS
        OnePlusSensorStore(context).startSessionForSensor("AA:BB:CC:DD:EE:11", startedAt, null)

        refreshLifecycle()

        verify(persistenceLayer, after(500L).never())
            .insertCgmSourceData(any(), any(), any(), anyOrNull())
        verify(rxBus, never()).send(any<EventRefreshOverview>())
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(startedAt)
    }

    @Test
    fun `a sensor whose therapy event is missing gets its age put back once`() = runTest {
        whenever(preferences.get(DexcomOnePlusBooleanKey.RepairMissingSensorChange)).thenReturn(true)
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
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
        whenever(preferences.get(DexcomOnePlusBooleanKey.RepairMissingSensorChange)).thenReturn(true)
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
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
        private const val MAC = "AA:BB:CC:DD:EE:01"
        private const val PRODUCTION_PREFS = "dexcom_oneplus_sensor"
        private const val KEY_SESSION_START = "session_start_ms"
    }
}
