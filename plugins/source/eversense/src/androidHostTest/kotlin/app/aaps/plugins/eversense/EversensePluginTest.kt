package app.aaps.plugins.eversense

import android.Manifest
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleCentral
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.mockito.kotlin.after
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.keys.PreferenceType
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.CalibrationMode
import app.aaps.core.interfaces.source.CgmDriver
import app.aaps.core.keys.LongNonKey
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.eversense.EversensePlugin
import app.aaps.plugins.eversense.R
import app.aaps.plugins.eversense.keys.EversenseBooleanKey
import app.aaps.plugins.eversense.pairing.EversensePairing
import app.aaps.plugins.eversense.keys.EversenseRuntimeKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EversensePluginTest {
    /** The settings are the cloud upload and the logging; the login is the pairing wizard's, not a setting to export. */
    @Test fun registersItsSettingsButNoLogin() {
        val preferences: Preferences = mock()
        val plugin = EversensePlugin(mock(), mock(), preferences, mock(), mock(), mock(), mock(), bleCentral, pairing)
        verify(preferences).registerPreferences(EversenseBooleanKey.entries + EversenseRuntimeKey.entries)
        assertThat(plugin.pluginDescription.mainType).isEqualTo(PluginType.BGSOURCE)
        assertThat(plugin.pluginDescription.composeContentProvider).isNotNull()
        assertThat(plugin.pluginDescription.defaultPlugin).isFalse()
        val screen = plugin.getPreferenceScreenContent()
        assertThat(screen.items).containsExactly(EversenseBooleanKey.EversenseCloudUploadEnabled, EversenseBooleanKey.EversenseDiagnosticLogging)
        assertThat(EversenseBooleanKey.EversenseDiagnosticLogging.defaultValue).isFalse()
        assertThat(EversenseBooleanKey.EversenseDiagnosticLogging.exportable).isFalse()
        assertThat(EversenseBooleanKey.entries.all { it.preferenceType == PreferenceType.SWITCH }).isTrue()
    }

    /** What the bottom bar's "!" shows is the pairing's: not paired, or the login refused. */
    @Test fun attentionIsThePairings() {
        val plugin = EversensePlugin(mock(), mock(), mock(), mock(), mock(), mock(), mock(), bleCentral, pairing)
        assertThat(plugin.attention).isSameInstanceAs(pairing.attention)
    }
    /** A ResourceHelper whose texts are their own resource id, so that a refusal is never null by accident. */
    private fun texts(): ResourceHelper = mock<ResourceHelper>().also { whenever(it.gs(anyInt())).thenAnswer { invocation -> "text ${invocation.arguments[0]}" } }

    private fun connectedBackend(accepts: Boolean = true): EversenseCGMPlugin = mock<EversenseCGMPlugin>().also {
        whenever(it.isConnected()).thenReturn(true)
        whenever(it.sendCalibration(any(), any())).thenReturn(accepts)
    }

    /** The user calibrates in the AAPS calibration dialog; the master hands the value to calibrate(). */
    @Test fun isACgmDriverThatTakesCalibrations() {
        val plugin = EversensePlugin(mock(), mock(), mock(), mock(), mock(), mock(), mock(), bleCentral, pairing)
        assertThat(plugin).isInstanceOf(CgmDriver::class.java)
        assertThat(plugin.calibrationMode).isEqualTo(CalibrationMode.SOURCE)
    }

    @Test fun successfulCalibrationUpdatesCountdownAndRequestsSync() = runBlocking {
        val preferences: Preferences = mock()
        val backend = connectedBackend()
        val plugin = EversensePlugin(texts(), mock(), preferences, mock(), mock(), { backend }, mock(), bleCentral, pairing)
        assertThat(plugin.calibrate(100.0)).isNull()
        verify(backend).sendCalibration(eq(100), any())
        verify(backend).triggerFullSync(force = true)
        verify(preferences).put(eq(LongNonKey.EversenseLastCalibrationSubmittedAt), any())
    }

    @Test fun failedCalibrationSaysSoAndDoesNotStartCountdown() = runBlocking {
        val preferences: Preferences = mock()
        val backend = connectedBackend(accepts = false)
        val plugin = EversensePlugin(texts(), mock(), preferences, mock(), mock(), { backend }, mock(), bleCentral, pairing)
        assertThat(plugin.calibrate(100.0)).isEqualTo("text ${R.string.eversense_calibration_failed}")
        verify(preferences, never()).put(eq(LongNonKey.EversenseLastCalibrationSubmittedAt), any())
        verify(backend, never()).triggerFullSync(any())
    }

    /** 2.2 mmol/L is 39.6 mg/dL: rounded to 40, not cut to 39, which the transmitter would refuse. */
    @Test fun calibrationValueIsRoundedNotTruncated() = runBlocking {
        val backend = connectedBackend()
        val plugin = EversensePlugin(texts(), mock(), mock(), mock(), mock(), { backend }, mock(), bleCentral, pairing)
        assertThat(plugin.calibrate(39.6)).isNull()
        verify(backend).sendCalibration(eq(40), any())
    }

    @Test fun calibrationOutsideTheTransmitterRangeIsRefusedWithoutSending() = runBlocking {
        val backend = connectedBackend()
        val plugin = EversensePlugin(texts(), mock(), mock(), mock(), mock(), { backend }, mock(), bleCentral, pairing)
        assertThat(plugin.calibrate(30.0)).isEqualTo("text ${R.string.eversense_calibration_invalid_value}")
        assertThat(plugin.calibrate(401.0)).isEqualTo("text ${R.string.eversense_calibration_invalid_value}")
        verify(backend, never()).sendCalibration(any(), any())
    }

    @Test fun readingsKeepValuesTimestampsTrendsAndModelWhenPassedToPersistence() = runBlocking {
        for ((type, sensor) in listOf(EversenseType.EVERSENSE_E3 to SourceSensor.EVERSENSE_E3, EversenseType.EVERSENSE_365 to SourceSensor.EVERSENSE_365)) {
            val backend: EversenseCGMPlugin = mock()
            whenever(backend.getCurrentState()).thenReturn(EversenseState())
            val persistence: PersistenceLayer = mock()
            val received = CompletableDeferred<List<GV>>()
            whenever(persistence.insertCgmSourceData(any(), any(), any(), anyOrNull())).thenAnswer {
                received.complete(it.getArgument(1))
                PersistenceLayer.TransactionResult<GV>()
            }
            val plugin = EversensePlugin(mock(), mock(), storedLogin(), mock(), mock(), { backend }, persistence, bleCentral, pairing)
            plugin.onStart()
            plugin.onCGMRead(type, listOf(EversenseCGMResult(123, 1_700_000_000_000L, EversenseTrendArrow.FLAT)))
            val values = withTimeout(5_000) { received.await() }
            assertThat(values.single().value).isEqualTo(123.0)
            assertThat(values.single().timestamp).isEqualTo(1_700_000_000_000L)
            assertThat(values.single().sourceSensor).isEqualTo(sensor)
            assertThat(values.single().trendArrow).isEqualTo(TrendArrow.FLAT)
            verify(persistence).insertCgmSourceData(Sources.Eversense, values, emptyList(), null)
            plugin.onStop()
        }
    }

    @Test fun settingsImportHooksDoNotRewindLiveDeviceState() {
        val preferences: Preferences = mock()
        val plugin = EversensePlugin(mock(), mock(), preferences, mock(), mock(), { error("Bluetooth must stay closed") }, mock(), bleCentral, pairing)
        plugin.beforeImport()
        plugin.afterImport()
        verify(preferences, never()).put(eq(EversenseRuntimeKey.DeviceState), any<String>())
    }

    @Test fun stoppingSourceAlsoStopsBackendAndRemovesWatcher() = runBlocking {
        val backend: EversenseCGMPlugin = mock()
        val plugin = EversensePlugin(mock(), mock(), mock(), mock(), mock(), { backend }, mock(), bleCentral, pairing)
        plugin.onStop()
        verify(backend).removeWatcher(plugin)
        verify(backend).stop()
    }

    // ----- lifecycle: a start loads everything, a stop drops everything -----

    /** Bluetooth as the app's central reports it; a test switches it off and on. */
    private val adapter = MutableStateFlow(AdapterState.ON)
    private val bleCentral: BleCentral = mock<BleCentral>().also { whenever(it.adapterState).thenReturn(adapter) }
    private val pairing: EversensePairing = mock<EversensePairing>().also { whenever(it.attention).thenReturn(MutableStateFlow(null)) }

    /** The preferences a started plugin reads: the switches it watches. The login is the driver's own. */
    private fun storedLogin(cloudUpload: Boolean = false): Preferences = mock<Preferences>().also {
        whenever(it.get(EversenseBooleanKey.EversenseCloudUploadEnabled)).thenReturn(cloudUpload)
        whenever(it.observe(EversenseBooleanKey.EversenseDiagnosticLogging)).thenReturn(MutableStateFlow(false))
    }

    /**
     * After a stop nothing of the run is left: the link is closed, a late reading is not stored, and the notices this
     * plugin clears itself when their condition ends are gone, because a stopped plugin no longer could.
     */
    @Test fun aStopDropsWhatTheStartBuilt() = runBlocking {
        val backend: EversenseCGMPlugin = mock()
        val persistence: PersistenceLayer = mock()
        val notifications: NotificationManager = mock()
        val plugin = EversensePlugin(mock(), mock(), storedLogin(), mock(), notifications, { backend }, persistence, bleCentral, pairing)
        plugin.onStart()
        plugin.sensorBatteryLevel = 40

        plugin.onStop()
        plugin.onCGMRead(EversenseType.EVERSENSE_365, listOf(EversenseCGMResult(123, 1_800_000_000_000L, EversenseTrendArrow.FLAT)))

        verify(backend).stop()
        verify(backend).removeWatcher(plugin)
        verify(notifications).dismiss(NotificationId.EVERSENSE_PLACEMENT)
        assertThat(plugin.sensorBatteryLevel).isEqualTo(-1)
        verify(persistence, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
    }

    /** A reload, as a settings import does it, gives the same transmitter: the second start reads the login again. */
    @Test fun aStartAfterAStopStartsAgainFromStorage() = runBlocking {
        val backend: EversenseCGMPlugin = mock()
        whenever(backend.getCurrentState()).thenReturn(EversenseState())
        val persistence: PersistenceLayer = mock()
        val received = CompletableDeferred<List<GV>>()
        whenever(persistence.insertCgmSourceData(any(), any(), any(), anyOrNull())).thenAnswer {
            received.complete(it.getArgument(1))
            PersistenceLayer.TransactionResult<GV>()
        }
        val plugin = EversensePlugin(mock(), mock(), storedLogin(), mock(), mock(), { backend }, persistence, bleCentral, pairing)

        plugin.onStart()
        plugin.onStop()
        plugin.onStart()
        plugin.onCGMRead(EversenseType.EVERSENSE_365, listOf(EversenseCGMResult(123, 1_800_000_000_000L, EversenseTrendArrow.FLAT)))

        assertThat(withTimeout(5_000) { received.await() }.single().value).isEqualTo(123.0)
        verify(backend, times(2)).addWatcher(plugin)
        verify(backend, times(2)).start()
        plugin.onStop()
    }

    /** Android can leave out the disconnect while Bluetooth is off: when it is back on, the driver reconnects. */
    @Test fun bluetoothBackOnReconnectsButTheStateAtStartDoesNot() = runBlocking {
        adapter.value = AdapterState.OFF   // Bluetooth is off when the plugin starts
        val backend: EversenseCGMPlugin = mock()
        val plugin = EversensePlugin(mock(), mock(), storedLogin(), mock(), mock(), { backend }, mock(), bleCentral, pairing)
        plugin.onStart()
        // The collector runs on its own thread: wait until it has read the state at start, which it skips
        withTimeout(5_000) { adapter.subscriptionCount.first { it > 0 } }
        delay(100)
        verify(backend, never()).onBluetoothAvailable()

        adapter.value = AdapterState.ON
        verify(backend, timeout(5_000)).onBluetoothAvailable()

        // Stopped: Bluetooth coming back is none of its business any more
        plugin.onStop()
        adapter.value = AdapterState.OFF
        adapter.value = AdapterState.ON
        verify(backend, after(300).times(1)).onBluetoothAvailable()
    }

    /** The Bluetooth permission is asked on the app's permission sheet, as a pump driver's, not by a screen at start. */
    @Test fun bluetoothPermissionIsAskedOnThePermissionSheet() {
        val plugin = EversensePlugin(mock(), mock(), mock(), mock(), mock(), { mock() }, mock(), bleCentral, pairing)
        assertThat(plugin.requiredPermissions().flatMap { it.permissions })
            .containsAtLeast(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    }

    @Test fun localGlucosePersistenceContinuesWhenCloudQueueFails() = runBlocking {
        val backend: EversenseCGMPlugin = mock()
        val preferences: Preferences = storedLogin(cloudUpload = true)
        whenever(backend.getCurrentState()).thenReturn(EversenseState())
        val cloudFailed = CompletableDeferred<Unit>()
        whenever(backend.cloudStore()).thenAnswer { cloudFailed.complete(Unit); throw IllegalStateException("cloud unavailable") }
        val persistence: PersistenceLayer = mock()
        val received = kotlinx.coroutines.channels.Channel<List<GV>>(2)
        whenever(persistence.insertCgmSourceData(any(), any(), any(), anyOrNull())).thenAnswer {
            received.trySend(it.getArgument(1))
            PersistenceLayer.TransactionResult<GV>()
        }
        val plugin = EversensePlugin(mock(), mock(), preferences, mock(), mock(), { backend }, persistence, bleCentral, pairing)
        plugin.onStart()
        try {
            plugin.onCGMRead(EversenseType.EVERSENSE_365, listOf(EversenseCGMResult(123, 1_800_000_000_000L, EversenseTrendArrow.FLAT)))
            assertThat(withTimeout(5_000) { received.receive() }.single().value).isEqualTo(123.0)
            withTimeout(5_000) { cloudFailed.await() }
            plugin.onCGMRead(EversenseType.EVERSENSE_365, listOf(EversenseCGMResult(124, 1_800_000_300_000L, EversenseTrendArrow.FLAT)))
            assertThat(withTimeout(5_000) { received.receive() }.single().value).isEqualTo(124.0)
            verify(backend, never()).connect()
            verify(backend, never()).disconnect()
        } finally { plugin.onStop() }
    }

}
