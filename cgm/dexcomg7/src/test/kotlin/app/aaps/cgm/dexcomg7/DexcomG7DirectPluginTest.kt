package app.aaps.cgm.dexcomg7

import android.content.Context
import app.aaps.cgm.dexcomg7.alarm.G7AlarmKeys
import app.aaps.cgm.dexcomg7.alarm.G7Alarms
import app.aaps.cgm.dexcomg7.data.G7State
import app.aaps.cgm.dexcomg7.data.G7StateStore
import app.aaps.cgm.dexcomg7.protocol.AlgorithmState
import app.aaps.cgm.dexcomg7.protocol.G7Trend
import app.aaps.cgm.dexcomg7.session.G7Alerts
import app.aaps.cgm.dexcomg7.session.G7PairingService
import app.aaps.cgm.dexcomg7.session.G7Reading
import app.aaps.cgm.dexcomg7.session.G7Session
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DexcomG7DirectPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var context: Context
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var store: G7StateStore
    @Mock lateinit var session: G7Session
    @Mock lateinit var pairing: G7PairingService
    @Mock lateinit var alerts: G7Alerts
    @Mock lateinit var alarms: G7Alarms
    @Mock lateinit var blePreCheck: BlePreCheck

    private lateinit var plugin: DexcomG7DirectPlugin

    private val now = System.currentTimeMillis()

    @BeforeEach
    fun setUp() {
        whenever(rh.gs(any<Int>())).thenReturn("")
        whenever(rh.gs(any<TextRef>())).thenReturn("")
        plugin = DexcomG7DirectPlugin(rh, aapsLogger, preferences, notificationManager, context, persistenceLayer, store, session, pairing, alerts, alarms, blePreCheck)
        whenever(store.value).thenReturn(G7State(address = "AA", activatedAt = now - 60_000_000))
    }

    private fun reading(glucose: Int?, state: Int = 6, displayOnly: Boolean = false, trend: G7Trend? = G7Trend.FLAT, at: Long = now) =
        G7Reading(at, glucose, trend, 0.0, AlgorithmState(state), displayOnly, fromBackfill = false)

    @Test
    fun storesOnlyUsableReadings() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(false)
        plugin.store(
            listOf(
                reading(120, at = now - 600_000),
                reading(130, state = 2, at = now - 300_000),   // warmup
                reading(104, displayOnly = true, at = now - 200_000), // calibration echo
                reading(null, at = now - 100_000),
                reading(500, trend = G7Trend.DOUBLE_UP, at = now)     // clamped to 400
            )
        )
        val captor = argumentCaptor<List<GV>>()
        verify(persistenceLayer).insertCgmSourceData(eq(Sources.Dexcom), captor.capture(), eq(emptyList()), isNull())
        assertThat(captor.firstValue.map { it.value }).containsExactly(120.0, 400.0).inOrder()
        assertThat(captor.firstValue.map { it.trendArrow }).containsExactly(TrendArrow.FLAT, TrendArrow.DOUBLE_UP).inOrder()
        assertThat(captor.firstValue.all { it.sourceSensor == SourceSensor.DEXCOM_G7_NATIVE }).isTrue()
    }

    @Test
    fun passesSensorStartWhenAsked() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
        plugin.store(listOf(reading(120)))
        verify(persistenceLayer).insertCgmSourceData(eq(Sources.Dexcom), any(), eq(emptyList()), eq(now - 60_000_000))
    }

    @Test
    fun storesNothingWhenNothingIsUsable() = runTest {
        whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(false)
        plugin.store(listOf(reading(130, state = 2)))
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), any())
    }

    @Test
    fun reportsSensorErrorWhenFailedOrOver() {
        whenever(store.value).thenReturn(G7State(address = "AA", activatedAt = now - 60_000_000, latestAlgorithmState = 25))
        assertThat(plugin.hasSensorError()).isTrue()
        whenever(store.value).thenReturn(G7State(address = "AA", sharedKeyHex = "00", activatedAt = now - 60_000_000, latestAlgorithmState = 6))
        assertThat(plugin.hasSensorError()).isFalse()
        whenever(store.value).thenReturn(G7State(address = "AA", sharedKeyHex = "00", activatedAt = now - 12L * 24 * 60 * 60 * 1000, latestAlgorithmState = 6))
        assertThat(plugin.hasSensorError()).isTrue()
    }

    @Test
    fun mapsEveryTrend() {
        assertThat(DexcomG7DirectPlugin.trendArrow(null)).isEqualTo(TrendArrow.NONE)
        assertThat(G7Trend.entries.map { DexcomG7DirectPlugin.trendArrow(it) }).containsNoDuplicates()
    }

    /**
     * The settings screen draws the plugin card and the sections in it, and silently drops a section
     * inside a section. So no section may hold another, and every alarm setting must be in one of them.
     */
    @Test
    fun everyAlarmSettingIsOnTheScreen() {
        val screen = plugin.getPreferenceScreenContent()
        val sections = screen.items.filterIsInstance<PreferenceSubScreenDef>()
        sections.forEach { assertThat(it.items.filterIsInstance<PreferenceSubScreenDef>()).isEmpty() }
        val shown = (screen.items + sections.flatMap { it.items }).filterIsInstance<PreferenceKey>().map { it.key }
        assertThat(shown).containsAtLeastElementsIn(G7AlarmKeys.all.map { it.key })
    }
}
