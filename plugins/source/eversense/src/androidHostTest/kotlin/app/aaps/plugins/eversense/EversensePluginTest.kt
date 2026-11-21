package app.aaps.plugins.eversense

import android.Manifest
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.source.CalibrationMode
import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.interfaces.source.CgmDriver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.CalibrationReadiness
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.keys.EversenseBooleanKey
import app.aaps.plugins.eversense.keys.EversenseRuntimeKey
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.pairing.EversenseAccount
import app.aaps.plugins.eversense.pairing.EversenseModel
import app.aaps.plugins.eversense.pairing.EversensePairing
import app.aaps.plugins.eversense.util.StorageKeys
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.after
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EversensePluginTest {

    /** Texts as their resource name, so a refusal is never null by accident. */
    private val texts = NameTextResolver()
    private val notifications = RecordingNotificationManager()
    private val dateUtil: DateUtil = mock<DateUtil>().also { whenever(it.now()).thenReturn(NOW) }

    /** Bluetooth as the app's central reports it; a test switches it off and on. */
    private val adapter = MutableStateFlow(AdapterState.ON)
    private val bleCentral: BleCentral = mock<BleCentral>().also { whenever(it.adapterState).thenReturn(adapter) }
    private val pairing: EversensePairing = mock<EversensePairing>().also {
        whenever(it.attention).thenReturn(MutableStateFlow(null))
        whenever(it.account).thenReturn(MutableStateFlow(EversenseAccount("user", EversenseModel.E3)))
    }

    /** The preferences a started plugin reads: the switches it watches. The login is the driver's own. */
    private fun storedLogin(cloudUpload: Boolean = false): Preferences = mock<Preferences>().also {
        whenever(it.get(EversenseBooleanKey.EversenseCloudUploadEnabled)).thenReturn(cloudUpload)
    }

    /** The driver, with its own store in memory. */
    private fun backend(): EversenseDriver = mock<EversenseDriver>().also {
        whenever(it.preferences).thenReturn(MemoryStore())
        whenever(it.getCurrentState()).thenReturn(EversenseState())
    }

    private fun connectedBackend(accepts: Boolean = true, readiness: CalibrationReadiness = CalibrationReadiness.READY): EversenseDriver =
        backend().also {
            whenever(it.isConnected()).thenReturn(true)
            runBlocking { whenever(it.sendCalibration(any(), any())).thenReturn(accepts) }
            whenever(it.getCurrentState()).thenReturn(EversenseState().apply { calibrationReadiness = readiness })
        }

    private fun plugin(
        backend: EversenseDriver = backend(),
        preferences: Preferences = storedLogin(),
        persistence: PersistenceLayer = mock(),
        pairing: EversensePairing = this.pairing,
        notifications: NotificationManager = this.notifications
    ) = EversensePlugin(texts, mock(), preferences, mock(), notifications, { backend }, dateUtil, persistence, bleCentral, pairing)

    private fun text(ref: TextRef) = texts.gs(ref)

    /** The only setting is the cloud upload (logging is AAPS's own); the login is the pairing wizard's, not a setting to export. */
    @Test fun registersItsSettingsButNoLogin() {
        val preferences: Preferences = mock()
        val plugin = plugin(preferences = preferences)
        verify(preferences).registerPreferences(EversenseBooleanKey.entries + EversenseRuntimeKey.entries)
        assertThat(plugin.pluginDescription.mainType).isEqualTo(PluginType.BGSOURCE)
        assertThat(plugin.pluginDescription.composeContentProvider).isNotNull()
        assertThat(plugin.pluginDescription.defaultPlugin).isFalse()
        val screen = plugin.getPreferenceScreenContent()
        assertThat(screen.items).containsExactly(EversenseBooleanKey.EversenseCloudUploadEnabled)
        assertThat(EversenseBooleanKey.entries.all { it.preferenceType == PreferenceType.SWITCH }).isTrue()
    }

    /** What the bottom bar's "!" shows is the pairing's: not paired, or the login refused. */
    @Test fun attentionIsThePairings() {
        assertThat(plugin().attention).isSameInstanceAs(pairing.attention)
    }

    /** The user calibrates in the AAPS calibration dialog; the master hands the value to calibrate(). */
    @Test fun isACgmDriverThatTakesCalibrations() {
        val plugin = plugin()
        assertThat(plugin).isInstanceOf(CgmDriver::class.java)
        assertThat(plugin.calibrationMode).isEqualTo(CalibrationMode.SOURCE)
    }

    @Test fun successfulCalibrationUpdatesCountdownAndRequestsSync(): Unit = runBlocking {
        val preferences: Preferences = mock()
        val backend = connectedBackend()
        val plugin = plugin(backend = backend, preferences = preferences)
        assertThat(plugin.calibrate(100.0)).isNull()
        verify(backend).sendCalibration(eq(100), any())
        verify(backend).triggerFullSync(force = true)
        verify(preferences).put(LongNonKey.EversenseLastCalibrationSubmittedAt, NOW)
    }

    @Test fun failedCalibrationSaysSoAndDoesNotStartCountdown(): Unit = runBlocking {
        val preferences: Preferences = mock()
        val backend = connectedBackend(accepts = false)
        val plugin = plugin(backend = backend, preferences = preferences)
        assertThat(plugin.calibrate(100.0)).isEqualTo(text(EversenseStrings.eversense_calibration_failed))
        verify(preferences, never()).put(eq(LongNonKey.EversenseLastCalibrationSubmittedAt), any())
        verify(backend, never()).triggerFullSync(any())
    }

    /** 2.2 mmol/L is 39.6 mg/dL: rounded to 40, not cut to 39, which the transmitter would refuse. */
    @Test fun calibrationValueIsRoundedNotTruncated(): Unit = runBlocking {
        val backend = connectedBackend()
        assertThat(plugin(backend = backend).calibrate(39.6)).isNull()
        verify(backend).sendCalibration(eq(40), any())
    }

    /** Not connected: the transmitter is connected first, and the calibration goes out once it is ready. */
    @Test fun calibrationConnectsTheTransmitterFirst(): Unit = runBlocking {
        val backend = connectedBackend()
        whenever(backend.isConnected()).thenReturn(false)
        val watchers = mutableListOf<EversenseWatcher>()
        whenever(backend.addWatcher(any())).thenAnswer { watchers += it.getArgument<EversenseWatcher>(0) }
        whenever(backend.connect()).thenAnswer {
            watchers.toList().forEach { watcher -> watcher.onTransmitterReady() }
            true
        }
        assertThat(plugin(backend = backend).calibrate(100.0)).isNull()
        verify(backend).connect()
        verify(backend).sendCalibration(eq(100), any())
        // The waiting watcher is gone again
        verify(backend).removeWatcher(watchers.single())
    }

    /** The confirmation says when the transmitter must be connected first, which can take a while. */
    @Test fun calibrationNoteSaysWhenTheTransmitterMustConnectFirst() {
        val backend = backend()
        val plugin = plugin(backend = backend)
        whenever(backend.isConnected()).thenReturn(true)
        assertThat(plugin.calibrationNote()).isNull()
        whenever(backend.isConnected()).thenReturn(false)
        assertThat(plugin.calibrationNote()).isEqualTo(text(EversenseStrings.eversense_calibration_note_reconnect))
    }

    /** No transmitter: said before the confirmation, and calibrate() does not wait for a link that cannot come. */
    @Test fun calibrationWithoutATransmitterIsRefusedAtOnce(): Unit = runBlocking {
        val backend = connectedBackend()
        val notPaired: EversensePairing = mock<EversensePairing>().also { whenever(it.attention).thenReturn(MutableStateFlow(CgmAttention.NOT_PAIRED)) }
        val plugin = plugin(backend = backend, pairing = notPaired)
        assertThat(plugin.calibrationRefusal()).isEqualTo(text(EversenseStrings.eversense_not_paired))
        assertThat(plugin.calibrate(100.0)).isEqualTo(text(EversenseStrings.eversense_not_paired))
        verify(backend, never()).sendCalibration(any(), any())
    }

    /**
     * An E3 says when it cannot take a calibration (glucose changing fast, too soon after the last one, ...). That is
     * said before the confirmation and nothing is sent. Not known yet is left to the transmitter.
     */
    @Test fun anE3ThatIsNotReadyRefusesTheCalibrationWithItsReason(): Unit = runBlocking {
        for ((readiness, reason) in listOf(
            CalibrationReadiness.GLUCOSE_RATE_TOO_HIGH to EversenseStrings.eversense_calibration_refused_rate,
            CalibrationReadiness.WAITING_POST_CALIBRATION to EversenseStrings.eversense_calibration_refused_too_soon,
            CalibrationReadiness.NO_SENSOR_LINKED to EversenseStrings.eversense_calibration_refused_sensor,
            CalibrationReadiness.DROPOUT_PHASE to EversenseStrings.eversense_calibration_refused_no_data,
            CalibrationReadiness.TRANSMITTER_EOL to EversenseStrings.eversense_calibration_refused_transmitter
        )) {
            val backend = connectedBackend(readiness = readiness)
            val plugin = plugin(backend = backend)
            assertThat(plugin.calibrationRefusal()).isEqualTo(text(reason))
            assertThat(plugin.calibrate(100.0)).isEqualTo(text(reason))
            verify(backend, never()).sendCalibration(any(), any())
        }
        for (readiness in listOf(CalibrationReadiness.READY, CalibrationReadiness.REASON_UNKNOWN)) {
            assertThat(plugin(backend = connectedBackend(readiness = readiness)).calibrationRefusal()).isNull()
        }
    }

    /** A refused login is no reason to refuse: the value goes to the transmitter over Bluetooth. */
    @Test fun aRefusedLoginDoesNotStopACalibration(): Unit = runBlocking {
        val signedOut: EversensePairing = mock<EversensePairing>().also { whenever(it.attention).thenReturn(MutableStateFlow(CgmAttention.SIGN_IN_FAILED)) }
        assertThat(plugin(backend = connectedBackend(), pairing = signedOut).calibrationRefusal()).isNull()
    }

    @Test fun calibrationOutsideTheTransmitterRangeIsRefusedWithoutSending(): Unit = runBlocking {
        val backend = connectedBackend()
        val plugin = plugin(backend = backend)
        assertThat(plugin.calibrate(30.0)).isEqualTo(text(EversenseStrings.eversense_calibration_invalid_value))
        assertThat(plugin.calibrate(401.0)).isEqualTo(text(EversenseStrings.eversense_calibration_invalid_value))
        verify(backend, never()).sendCalibration(any(), any())
    }

    // ----- readings -----

    /** A persistence layer that hands each stored batch to the test. */
    private fun recordingPersistence(received: Channel<List<GV>>): PersistenceLayer = mock<PersistenceLayer>().also {
        runBlocking {
            whenever(it.insertCgmSourceData(any(), any(), any(), anyOrNull())).thenAnswer { call ->
                received.trySend(call.getArgument(1))
                PersistenceLayer.TransactionResult<GV>()
            }
        }
    }

    @Test fun readingsKeepValuesTimestampsTrendsAndModelWhenPassedToPersistence(): Unit = runBlocking {
        for ((type, sensor) in listOf(EversenseType.EVERSENSE_E3 to SourceSensor.EVERSENSE_E3, EversenseType.EVERSENSE_365 to SourceSensor.EVERSENSE_365)) {
            val received = Channel<List<GV>>(Channel.UNLIMITED)
            val persistence = recordingPersistence(received)
            val plugin = plugin(persistence = persistence)
            plugin.onStart()
            plugin.onCGMRead(type, listOf(EversenseCGMResult(123, 1_700_000_000_000L, EversenseTrendArrow.FLAT)))
            val values = withTimeout(5_000) { received.receive() }
            assertThat(values.single().value).isEqualTo(123.0)
            assertThat(values.single().timestamp).isEqualTo(1_700_000_000_000L)
            assertThat(values.single().sourceSensor).isEqualTo(sensor)
            assertThat(values.single().trendArrow).isEqualTo(TrendArrow.FLAT)
            verify(persistence).insertCgmSourceData(Sources.Eversense, values, emptyList(), null)
            plugin.onStop()
        }
    }

    /** Only a 365 gives a stable insertion time: a reading of it keeps the sensor change in step, an E3 reading does not. */
    @Test fun onlyA365ReadingCarriesTheSensorInsertion(): Unit = runBlocking {
        for ((type, insertion) in listOf(EversenseType.EVERSENSE_365 to 1_690_000_000_000L, EversenseType.EVERSENSE_E3 to null)) {
            val backend = backend()
            whenever(backend.getCurrentState()).thenReturn(EversenseState().apply { insertionDate = 1_690_000_000_000L })
            val received = Channel<List<GV>>(Channel.UNLIMITED)
            val persistence = recordingPersistence(received)
            val plugin = plugin(backend = backend, persistence = persistence)
            plugin.onStart()
            plugin.onCGMRead(type, listOf(EversenseCGMResult(123, 1_700_000_000_000L, EversenseTrendArrow.FLAT)))
            val values = withTimeout(5_000) { received.receive() }
            verify(persistence).insertCgmSourceData(Sources.Eversense, values, emptyList(), insertion)
            plugin.onStop()
        }
    }

    @Test fun settingsImportHooksDoNotRewindLiveDeviceState() {
        val preferences: Preferences = mock()
        val plugin = EversensePlugin(texts, mock(), preferences, mock(), notifications, { error("Bluetooth must stay closed") }, dateUtil, mock(), bleCentral, pairing)
        plugin.beforeImport()
        plugin.afterImport()
        verify(preferences, never()).put(eq(EversenseRuntimeKey.DeviceState), any<String>())
    }

    // ----- lifecycle: a start loads everything, a stop drops everything -----

    @Test fun stoppingSourceAlsoStopsBackendAndRemovesWatcher(): Unit = runBlocking {
        val backend = backend()
        val plugin = plugin(backend = backend)
        plugin.onStop()
        verify(backend).removeWatcher(plugin)
        verify(backend).stop()
    }

    /**
     * After a stop nothing of the run is left: the link is closed, a late reading is not stored, and the notices this
     * plugin clears itself when their condition ends are gone, because a stopped plugin no longer could.
     */
    @Test fun aStopDropsWhatTheStartBuilt(): Unit = runBlocking {
        val backend = backend()
        val persistence: PersistenceLayer = mock()
        val plugin = plugin(backend = backend, persistence = persistence)
        plugin.onStart()
        plugin.sensorBatteryLevel = 40

        plugin.onStop()
        plugin.onCGMRead(EversenseType.EVERSENSE_365, listOf(EversenseCGMResult(123, 1_800_000_000_000L, EversenseTrendArrow.FLAT)))
        plugin.onAlarmReceived(ActiveAlarm(EversenseAlarm.LOW_GLUCOSE, EversenseAlarm.LOW_GLUCOSE.code, 0, 0))
        plugin.onTransmitterNotPlaced()

        verify(backend).stop()
        verify(backend).removeWatcher(plugin)
        assertThat(notifications.dismissedIds).contains(NotificationId.EVERSENSE_PLACEMENT)
        assertThat(notifications.posted).isEmpty()
        assertThat(plugin.sensorBatteryLevel).isEqualTo(-1)
        verify(persistence, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
    }

    /** A reload, as a settings import does it, gives the same transmitter: the second start reads the login again. */
    @Test fun aStartAfterAStopStartsAgainFromStorage(): Unit = runBlocking {
        val backend = backend()
        val received = Channel<List<GV>>(Channel.UNLIMITED)
        val plugin = plugin(backend = backend, persistence = recordingPersistence(received))

        plugin.onStart()
        plugin.onStop()
        plugin.onStart()
        plugin.onCGMRead(EversenseType.EVERSENSE_365, listOf(EversenseCGMResult(123, 1_800_000_000_000L, EversenseTrendArrow.FLAT)))

        assertThat(withTimeout(5_000) { received.receive() }.single().value).isEqualTo(123.0)
        verify(backend, times(2)).addWatcher(plugin)
        verify(backend, times(2)).start()
        plugin.onStop()
    }

    /** Android can leave out the disconnect while Bluetooth is off: when it is back on, the driver reconnects. */
    @Test fun bluetoothBackOnReconnectsButTheStateAtStartDoesNot(): Unit = runBlocking {
        adapter.value = AdapterState.OFF   // Bluetooth is off when the plugin starts
        val backend = backend()
        val plugin = plugin(backend = backend)
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
        assertThat(plugin().requiredPermissions().flatMap { it.permissions })
            .containsAtLeast(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    }

    // ----- the cloud upload -----

    /** An E3 used without a login: the reading is stored, and nothing is queued for a cloud it cannot sign in to. */
    @Test fun anE3WithoutALoginQueuesNoCloudUpload(): Unit = runBlocking {
        val backend = backend()
        val cloudUsed = CompletableDeferred<Unit>()
        whenever(backend.cloudStore()).thenAnswer { cloudUsed.complete(Unit); null }
        val noLogin: EversensePairing = mock<EversensePairing>().also {
            whenever(it.attention).thenReturn(MutableStateFlow(null))
            whenever(it.account).thenReturn(MutableStateFlow(null))
        }
        val received = Channel<List<GV>>(Channel.UNLIMITED)
        val plugin = plugin(backend = backend, preferences = storedLogin(cloudUpload = true), persistence = recordingPersistence(received), pairing = noLogin)
        plugin.onStart()
        try {
            plugin.onCGMRead(EversenseType.EVERSENSE_E3, listOf(EversenseCGMResult(123, 1_800_000_000_000L, EversenseTrendArrow.FLAT)))
            withTimeout(5_000) { received.receive() }
            // The queue step comes right after the store; with a login it reaches the cloud store at once
            assertThat(withTimeoutOrNull(500) { cloudUsed.await() }).isNull()
        } finally {
            plugin.onStop()
        }
    }

    @Test fun localGlucosePersistenceContinuesWhenCloudQueueFails(): Unit = runBlocking {
        val backend = backend()
        val cloudFailed = CompletableDeferred<Unit>()
        whenever(backend.cloudStore()).thenAnswer { cloudFailed.complete(Unit); throw IllegalStateException("cloud unavailable") }
        val received = Channel<List<GV>>(Channel.UNLIMITED)
        val plugin = plugin(backend = backend, preferences = storedLogin(cloudUpload = true), persistence = recordingPersistence(received))
        plugin.onStart()
        try {
            plugin.onCGMRead(EversenseType.EVERSENSE_365, listOf(EversenseCGMResult(123, 1_800_000_000_000L, EversenseTrendArrow.FLAT)))
            assertThat(withTimeout(5_000) { received.receive() }.single().value).isEqualTo(123.0)
            withTimeout(5_000) { cloudFailed.await() }
            plugin.onCGMRead(EversenseType.EVERSENSE_365, listOf(EversenseCGMResult(124, 1_800_000_300_000L, EversenseTrendArrow.FLAT)))
            assertThat(withTimeout(5_000) { received.receive() }.single().value).isEqualTo(124.0)
            verify(backend, never()).connect()
            verify(backend, never()).disconnect()
        } finally {
            plugin.onStop()
        }
    }

    // ----- transmitter alarms -----

    private fun alarm(code: EversenseAlarm) = ActiveAlarm(code, code.code, 0, 0)

    /** One card per alarm: a second alarm does not silence the first. */
    @Test fun aSecondAlarmDoesNotDismissTheFirstAlarmsCard(): Unit = runBlocking {
        val plugin = plugin()
        plugin.onStart()
        try {
            plugin.onAlarmReceived(alarm(EversenseAlarm.LOW_GLUCOSE))
            plugin.onAlarmReceived(alarm(EversenseAlarm.HIGH_GLUCOSE))
            assertThat(notifications.dismissedHandles).isEmpty()
            assertThat(notifications.dismissedIds).doesNotContain(NotificationId.EVERSENSE_ALARM)
            assertThat(notifications.shown.filter { it.id == NotificationId.EVERSENSE_ALARM }.map { it.text })
                .containsExactly(text(EversenseAlarm.LOW_GLUCOSE.title), text(EversenseAlarm.HIGH_GLUCOSE.title))
        } finally {
            plugin.onStop()
        }
    }

    /** A repeat of the same alarm replaces its own card instead of adding another. */
    @Test fun aRepeatOfTheSameAlarmReplacesItsOwnCard(): Unit = runBlocking {
        val plugin = plugin()
        plugin.onStart()
        try {
            plugin.onAlarmReceived(alarm(EversenseAlarm.LOW_GLUCOSE))
            plugin.onAlarmReceived(alarm(EversenseAlarm.RATE_FALLING))
            val first = notifications.posted.first().handle
            plugin.onAlarmReceived(alarm(EversenseAlarm.LOW_GLUCOSE))
            assertThat(notifications.dismissedHandles).containsExactly(first)
            assertThat(notifications.shown.map { it.text })
                .containsExactly(text(EversenseAlarm.RATE_FALLING.title), text(EversenseAlarm.LOW_GLUCOSE.title))
        } finally {
            plugin.onStop()
        }
    }

    /** AAPS holds the transmitter link, so the vendor app cannot sound a critical alarm: it rings here; a warning does not. */
    @Test fun aCriticalAlarmRingsAndAWarningDoesNot(): Unit = runBlocking {
        val plugin = plugin()
        plugin.onStart()
        try {
            plugin.onAlarmReceived(alarm(EversenseAlarm.LOW_GLUCOSE))
            plugin.onAlarmReceived(alarm(EversenseAlarm.RATE_FALLING))
            plugin.onAlarmReceived(alarm(EversenseAlarm.ONE_CAL))
            assertThat(notifications.posted.map { it.level to it.sound }).containsExactly(
                NotificationLevel.URGENT to AlarmSound.ALARM,
                NotificationLevel.NORMAL to null,
                NotificationLevel.INFO to null
            ).inOrder()
        } finally {
            plugin.onStop()
        }
    }

    /**
     * The transmitter pushes a code with no meaning right after the placement guide closes: that one is dropped. Any
     * other time an unknown code still shows, as an unknown error.
     */
    @Test fun anUnknownAlarmIsDroppedOnlyRightAfterThePlacementGuide(): Unit = runBlocking {
        val backend = backend()
        val plugin = plugin(backend = backend)
        plugin.onStart()
        try {
            whenever(backend.lastPositioningModeExitAt).thenReturn(NOW - 2_000)
            plugin.onAlarmReceived(alarm(EversenseAlarm.UNKNOWN))
            assertThat(notifications.posted).isEmpty()
            whenever(backend.lastPositioningModeExitAt).thenReturn(NOW - 60_000)
            plugin.onAlarmReceived(alarm(EversenseAlarm.UNKNOWN))
            assertThat(notifications.posted.map { it.text }).containsExactly(text(EversenseAlarm.UNKNOWN.title))
        } finally {
            plugin.onStop()
        }
    }

    /** Code 0 is sent for a fault and for an overdue calibration: with the calibration overdue it says so. */
    @Test fun aCriticalFaultWithACalibrationOverdueSaysCalibrateNow(): Unit = runBlocking {
        val backend = backend()
        val store = MemoryStore()
        store.values[StorageKeys.STATE] = """{"nextCalibrationDate":${NOW - 1}}"""
        whenever(backend.preferences).thenReturn(store)
        val plugin = plugin(backend = backend)
        plugin.onStart()
        try {
            plugin.onAlarmReceived(alarm(EversenseAlarm.CRITICAL_FAULT))
            assertThat(notifications.posted.single().text).isEqualTo(text(EversenseStrings.eversense_calibration_due_now))
        } finally {
            plugin.onStop()
        }
    }

    @Test fun aTransmitterNotPlacedIsAnUrgentNotice(): Unit = runBlocking {
        val plugin = plugin()
        plugin.onStart()
        try {
            plugin.onTransmitterNotPlaced()
            val notice = notifications.posted.single()
            assertThat(notice.id).isEqualTo(NotificationId.EVERSENSE_PLACEMENT)
            assertThat(notice.level).isEqualTo(NotificationLevel.URGENT)
        } finally {
            plugin.onStop()
        }
    }

    companion object {

        const val NOW = 1_800_000_000_000L
    }
}
