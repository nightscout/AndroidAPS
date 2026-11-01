package app.aaps.plugins.source

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.plugins.source.DexcomOnePlusSensorStartCorrection.Verdict
import app.aaps.plugins.source.keys.DexcomOnePlusBooleanKey
import app.aaps.shared.tests.SharedPreferencesMock
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.reflect.Field

/**
 * Insertion-date correction, `DexcomOnePlusPlugin.correctProductionSensorStart`.
 * The setting is on by default; the tests set it explicitly.
 */
class DexcomOnePlusInsertionDateTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config
    @Mock lateinit var context: Context
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var notificationManager: NotificationManager

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
            if (stamp != null) result.sensorInsertionsInserted.add(sensorChange(stamp))
            result
        }
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(emptyList())
        whenever(persistenceLayer.getTherapyEventDataIncludingInvalidFromTime(any(), any())).thenReturn(emptyList())
        plugin = DexcomOnePlusPlugin(
            rh, aapsLogger, preferences, config, notificationManager, DexcomOnePlusAndroidHost(context), persistenceLayer,
            mock<DexcomOnePlusWarmupBasalGuard>(), activePlugin, rxBus,
        )
    }

    @Test
    fun `the correction switch is on by default`() {
        assertThat(DexcomOnePlusBooleanKey.CorrectSensorStart.defaultValue).isTrue()
    }

    @Test
    fun `a correction while the switch is off writes nothing`() = runTest {
        whenever(preferences.get(DexcomOnePlusBooleanKey.CorrectSensorStart)).thenReturn(false)
        val current = System.currentTimeMillis() - 2 * HOUR_MS
        OnePlusSensorStore(context).startSessionForSensor(MAC, current, null)

        val verdict = plugin.correctProductionSensorStart(current - 6 * HOUR_MS)

        assertThat(verdict).isEqualTo(Verdict.Disabled)
        assertNothingWritten(current)
    }

    @Test
    fun `InFuture invalidates nothing`() = runTest {
        enableCorrection()
        val current = System.currentTimeMillis() - 2 * HOUR_MS
        OnePlusSensorStore(context).startSessionForSensor(MAC, current, null)

        val verdict = plugin.correctProductionSensorStart(System.currentTimeMillis() + HOUR_MS)

        assertThat(verdict).isEqualTo(Verdict.InFuture)
        assertNothingWritten(current)
    }

    @Test
    fun `TooOld invalidates nothing`() = runTest {
        enableCorrection()
        val current = System.currentTimeMillis() - 2 * HOUR_MS
        OnePlusSensorStore(context).startSessionForSensor(MAC, current, null)

        val verdict = plugin.correctProductionSensorStart(System.currentTimeMillis() - 11 * 24 * HOUR_MS)

        assertThat(verdict).isEqualTo(Verdict.TooOld)
        assertNothingWritten(current)
    }

    @Test
    fun `NoSession invalidates nothing`() = runTest {
        enableCorrection()

        val verdict = plugin.correctProductionSensorStart(System.currentTimeMillis() - HOUR_MS)

        assertThat(verdict).isEqualTo(Verdict.NoSession)
        verify(persistenceLayer, never()).invalidateTherapyEvent(any(), any(), any(), anyOrNull(), any())
        verify(persistenceLayer, never()).invalidateCalibrationEntry(any(), any(), any(), anyOrNull(), any())
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(0L)
    }

    @Test
    fun `Accepted invalidates the sensor change then rewrites it`() = runTest {
        enableCorrection()
        val now = System.currentTimeMillis()
        val current = now - 2 * HOUR_MS
        val asked = now - 8 * HOUR_MS
        OnePlusSensorStore(context).startSessionForSensor(MAC, current, null)
        val event = sensorChange(current, id = 42L)
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(listOf(event))
        whenever(persistenceLayer.getTherapyEventDataIncludingInvalidFromTime(any(), any())).thenReturn(listOf(event))

        val verdict = plugin.correctProductionSensorStart(asked)

        assertThat(verdict).isEqualTo(Verdict.Accepted)
        verify(persistenceLayer).invalidateTherapyEvent(
            eq(42L), eq(Action.CAREPORTAL_REMOVED), any(), anyOrNull(), any(),
        )
        verify(persistenceLayer).insertCgmSourceData(any(), any(), any(), eq(asked))
        verify(persistenceLayer, never()).invalidateCalibrationEntry(any(), any(), any(), anyOrNull(), any())
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(asked)
        verify(rxBus).send(any())
    }

    @Test
    fun `an occupied timestamp moves one second and not further`() = runTest {
        enableCorrection()
        val now = System.currentTimeMillis()
        val current = now - 2 * HOUR_MS
        val asked = now - 8 * HOUR_MS
        OnePlusSensorStore(context).startSessionForSensor(MAC, current, null)
        val blocking = sensorChange(asked, id = 7L)
        whenever(persistenceLayer.getTherapyEventDataIncludingInvalidFromTime(any(), any())).thenReturn(listOf(blocking))
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(emptyList())

        val verdict = plugin.correctProductionSensorStart(asked)

        assertThat(verdict).isEqualTo(Verdict.Accepted)
        verify(persistenceLayer).insertCgmSourceData(any(), any(), any(), eq(asked + 1_000L))
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(asked + 1_000L)
        verify(persistenceLayer, never()).invalidateCalibrationEntry(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `overwriteSessionStart ignores a non positive time`() {
        val current = 1_700_000_000_000L
        val store = OnePlusSensorStore(context)
        store.startSessionForSensor(MAC, current, null)

        store.overwriteSessionStart(0L)
        store.overwriteSessionStart(-1L)

        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(current)
        store.overwriteSessionStart(current - HOUR_MS)
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(current - HOUR_MS)
    }

    private fun enableCorrection() {
        whenever(preferences.get(DexcomOnePlusBooleanKey.CorrectSensorStart)).thenReturn(true)
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
    }

    private suspend fun assertNothingWritten(currentStart: Long) {
        verify(persistenceLayer, never()).invalidateTherapyEvent(any(), any(), any(), anyOrNull(), any())
        verify(persistenceLayer, never()).invalidateCalibrationEntry(any(), any(), any(), anyOrNull(), any())
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
        verify(rxBus, never()).send(any())
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(currentStart)
    }

    private fun sensorChange(timestamp: Long, id: Long = 11L): TE = TE(
        id = id,
        timestamp = timestamp,
        type = TE.Type.SENSOR_CHANGE,
        glucoseUnit = GlucoseUnit.MGDL,
    )

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

    companion object {
        private const val HOUR_MS = 60L * 60L * 1000L
        private const val MAC = "AA:BB:CC:DD:EE:30"
        private const val PRODUCTION_PREFS = "dexcom_oneplus_sensor"
        private const val KEY_SESSION_START = "session_start_ms"
    }
}
