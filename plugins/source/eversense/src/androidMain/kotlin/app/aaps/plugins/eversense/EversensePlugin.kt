package app.aaps.plugins.eversense

import android.os.SystemClock
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.bluetoothPermissionGroup
import app.aaps.plugins.eversense.cloud.EversenseCloudUploader
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.storage.edit
import app.aaps.plugins.eversense.util.EversenseHttp365Util
import app.aaps.plugins.eversense.util.EversenseHttpE3Util
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.CalibrationMode
import app.aaps.core.interfaces.source.CgmDriver
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.ui.EversenseComposeContent
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.util.EversenseLogger
import app.aaps.plugins.eversense.util.StorageKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import app.aaps.core.keys.interfaces.withClick
import app.aaps.plugins.eversense.keys.EversenseIntentKey
import app.aaps.plugins.eversense.keys.EversenseStringKey
import app.aaps.core.ui.compose.icons.IcPluginEversense
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.source.AbstractBgSourcePlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey as MetroIntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.eversense.keys.EversenseBooleanKey
import app.aaps.plugins.eversense.keys.EversenseRuntimeKey
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@MetroIntKey(415)
@SingleIn(AppScope::class)
@Inject
class EversensePlugin(
    override val rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
    notificationManager: NotificationManager,
    private val eversenseProvider: () -> EversenseCGMPlugin,
    private val persistenceLayer: PersistenceLayer,
    private val bleCentral: BleCentral
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { _ -> EversenseComposeContent(title = rh.gs(R.string.source_eversense)) }
        .icon(IcPluginEversense)
        .pluginName(TextRef.AndroidRes(R.string.source_eversense))
        .preferencesVisibleInSimpleMode(false)
        .description(TextRef.AndroidRes(R.string.description_source_eversense)),
    ownPreferences = EversenseStringKey.entries + EversenseBooleanKey.entries + EversenseIntentKey.entries + EversenseRuntimeKey.entries,
    aapsLogger, rh, preferences, config, notificationManager
), CgmDriver, EversenseWatcher {

    private val eversense get() = eversenseProvider()

    companion object {

        // See onAlarmReceived()'s isSpuriousPostExitUnknown comment: the transmitter's unrecognized
        // post-exit status push arrives ~1-2s after exitPositioningMode() per device logs; this is a
        // generous margin over that without being wide enough to risk masking an unrelated
        // genuinely-unrecognized alarm that happens to follow shortly after someone closes the
        // placement guide.
        private const val POST_EXIT_UNKNOWN_ALARM_SUPPRESS_WINDOW_MS = 5000L

        /** The values the transmitter takes as a calibration, in mg/dL */
        private val CALIBRATION_RANGE_MGDL = 40..400
    }


    override var sensorBatteryLevel = -1

    /**
     * What one start of the plugin owns: its coroutines, the cloud upload in progress and the counters in memory.
     * [onStop] drops it whole, and the next [onStart] builds a new one from storage, so nothing of a stopped run is
     * trusted again. Null while the plugin is stopped: a callback that arrives late finds nothing to work with.
     */
    private class Running(val scope: CoroutineScope, val cloudUploader: EversenseCloudUploader) {

        /** Readings in a row with no sensor signal: three mean the transmitter is not over the sensor. */
        var consecutiveNoSignalReadings = 0
    }

    @Volatile private var running: Running? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val securePrefs: EversenseStore get() = eversense.preferences

    private fun cloudUploadEnabled() = preferences.get(EversenseBooleanKey.EversenseCloudUploadEnabled)

    private val lastNotifiedFirmwareVersion: String get() = securePrefs.getString("last_notified_firmware_version", "") ?: ""
    private fun setLastNotifiedFirmwareVersion(version: String) = securePrefs.edit(commit = true) { putString("last_notified_firmware_version", version) }
    private fun isSensorExpiryDismissed(insertionDate: Long, days: Int): Boolean =
        securePrefs.getBoolean("eversense_expiry_dismissed_${insertionDate}_${days}", false)
    private fun setSensorExpiryDismissed(insertionDate: Long, days: Int) =
        securePrefs.edit(commit = true) { putBoolean("eversense_expiry_dismissed_${insertionDate}_${days}", true) }
    private fun isCalibrationDueDismissed(nextCalibrationDate: Long): Boolean =
        securePrefs.getBoolean("eversense_cal_due_dismissed_${nextCalibrationDate}", false)
    private fun setCalibrationDueDismissed(nextCalibrationDate: Long) =
        securePrefs.edit(commit = true) { putBoolean("eversense_cal_due_dismissed_${nextCalibrationDate}", true) }
    private fun isBatteryLowDismissed(): Boolean =
        securePrefs.getBoolean("eversense_battery_low_dismissed", false)
    private fun setBatteryLowDismissed() =
        securePrefs.edit(commit = true) { putBoolean("eversense_battery_low_dismissed", true) }
    private val NO_SIGNAL_WARNING_THRESHOLD = 3
    private val CALIBRATION_RECONNECT_TIMEOUT_MS = 30_000L

    /** Asked for on the app's permission sheet, as a pump driver's; a start never opens a screen of its own. */
    override fun requiredPermissions(): List<PermissionGroup> = listOfNotNull(bluetoothPermissionGroup())

    /**
     * Loads everything from storage and connects: the login goes to the driver, which then finds the transmitter and
     * its keys in its own store. A start after a stop is a fresh start: [Running] is new, and so are the driver's
     * counters.
     */
    override suspend fun onStart() {
        super.onStart()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        running = Running(scope, newCloudUploader(scope))
        EversenseLogger.setDiagnosticLogging(preferences.get(EversenseBooleanKey.EversenseDiagnosticLogging))
        preferences.observe(EversenseBooleanKey.EversenseDiagnosticLogging)
            .onEach { EversenseLogger.setDiagnosticLogging(it) }
            .launchIn(scope)
        eversense.addWatcher(this)
        // Always sync credentials on startup — eversense.is365() is false until first connect
        // so we must set username/password unconditionally for new phone first-boot
        checkCredentialsNotification()
        eversense.start()
        // Android can leave out the disconnect callback while Bluetooth is off: reconnect when it is back on. The
        // current state is skipped, the start above has just connected.
        bleCentral.adapterState.drop(1).filter { it == AdapterState.ON }
            .onEach {
                aapsLogger.info(LTag.BGSOURCE, "Bluetooth turned back on — forcing Eversense reconnect")
                eversense.onBluetoothAvailable()
            }
            .launchIn(scope)
        combine(
            preferences.observe(EversenseStringKey.EversenseUsername),
            preferences.observe(EversenseStringKey.EversensePassword),
            preferences.observe(EversenseBooleanKey.EversenseEuropeanRegion)
        ) { user, password, region -> Triple(user, password, region) }
            .onEach { checkCredentialsNotification() }
            .launchIn(scope)
    }

    /**
     * Drops everything [onStart] built: the coroutines with the cloud upload in progress, the link and its retries
     * (the driver's stop), the counters, and the notices this plugin clears itself when their condition ends - once
     * stopped, it no longer could. Readings and the upload queue are stored and stay.
     */
    override suspend fun onStop() {
        super.onStop()
        running?.scope?.cancel()
        running = null
        eversense.removeWatcher(this)
        eversense.stop()
        EversenseLogger.setDiagnosticLogging(false)
        sensorBatteryLevel = -1
        notificationManager.dismiss(NotificationId.EVERSENSE_CREDENTIALS)
        notificationManager.dismiss(NotificationId.EVERSENSE_PLACEMENT)
    }

    // ── CgmDriver ── The user enters the finger-stick value in the AAPS calibration dialog; the master's
    // record writer hands it to calibrate() below.

    override val calibrationMode = CalibrationMode.SOURCE

    override suspend fun calibrate(bgMgdl: Double): String? = withContext(Dispatchers.IO) {
        // Rounded, not truncated: 2.2 mmol/L is 39.6 mg/dL and must not become 39
        val value = bgMgdl.roundToInt()
        if (value !in CALIBRATION_RANGE_MGDL) return@withContext rh.gs(R.string.eversense_calibration_invalid_value)
        if (!eversense.isConnected()) {
            aapsLogger.info(LTag.BGSOURCE, "Eversense calibration: not connected — reconnecting first")
            val reconnected = withTimeoutOrNull(CALIBRATION_RECONNECT_TIMEOUT_MS) { awaitTransmitterReady() }
            if (reconnected == null) {
                aapsLogger.warn(LTag.BGSOURCE, "Eversense calibration: reconnect timed out")
                return@withContext rh.gs(R.string.eversense_calibration_failed)
            }
        }
        val success = eversense.sendCalibration(value)
        aapsLogger.info(LTag.BGSOURCE, "Eversense calibration result: $success")
        if (!success) return@withContext rh.gs(R.string.eversense_calibration_failed)
        eversense.triggerFullSync(force = true)
        // Drives the home-screen "Calibration in progress" countdown banner.
        preferences.put(LongNonKey.EversenseLastCalibrationSubmittedAt, System.currentTimeMillis())
        null
    }

    // Bridges EversenseWatcher's callback-based "connected" signal into a single-shot coroutine
    // wait, cancelled (and its watcher cleaned up) automatically by the caller's withTimeoutOrNull.
    private suspend fun awaitTransmitterReady(): Boolean = suspendCancellableCoroutine { cont ->
        lateinit var watcher: EversenseWatcher
        watcher = object : EversenseWatcher {
            override fun onTransmitterReady() {
                if (cont.isActive) {
                    eversense.removeWatcher(watcher)
                    cont.resume(true)
                }
            }
            override fun onConnectionChanged(connected: Boolean) {}
            override fun onStateChanged(state: EversenseState) {}
            override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {}
            override fun onAlarmReceived(alarm: ActiveAlarm) {}
            override fun onTransmitterNotPlaced() {}
        }
        eversense.addWatcher(watcher)
        cont.invokeOnCancellation { eversense.removeWatcher(watcher) }
        eversense.connect(null)
    }

    @Synchronized
    private fun checkCredentialsNotification() {
        val username = preferences.get(EversenseStringKey.EversenseUsername)
        val password = preferences.get(EversenseStringKey.EversensePassword)
        if (username.isEmpty() || password.isEmpty()) {
            eversense.clearCredentials()
            notificationManager.post(
                NotificationId.EVERSENSE_CREDENTIALS,
                rh.gs(R.string.eversense_credentials_missing),
                level = NotificationLevel.URGENT
            )
        } else {
            eversense.setCredentials(username, password, preferences.get(EversenseBooleanKey.EversenseEuropeanRegion))
            notificationManager.dismiss(NotificationId.EVERSENSE_CREDENTIALS)
        }
    }

    // Status and connection, calibration, placement and the documentation are on the plugin's own screen
    // (EversenseComposeContent), opened from Manage as a pump's screen is.
    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "eversense_settings",
        title = TextRef.AndroidRes(R.string.source_eversense),
        items = listOf(
            EversenseBooleanKey.EversenseCloudUploadEnabled,
            EversenseBooleanKey.EversenseDiagnosticLogging,
            PreferenceSubScreenDef(
                key = "eversense_credentials_screen",
                title = TextRef.AndroidRes(R.string.eversense_credentials_title),
                items = listOf(
                    EversenseStringKey.EversenseUsername,
                    EversenseStringKey.EversensePassword,
                    EversenseBooleanKey.EversenseEuropeanRegion,
                    EversenseIntentKey.EversenseSignOut.withClick {
                        synchronized(this) {
                            preferences.put(EversenseStringKey.EversenseUsername, "")
                            preferences.put(EversenseStringKey.EversensePassword, "")
                            eversense.clearCredentials()
                        }
                    }
                )
            )
        ),
        icon = pluginDescription.icon
    )

    private fun signalToLabel(strength: Int): String = when {
        strength >= 75 -> rh.gs(R.string.eversense_signal_excellent)
        strength >= 48 -> rh.gs(R.string.eversense_signal_good)
        strength >= 30 -> rh.gs(R.string.eversense_signal_fair)
        strength >= 25 -> rh.gs(R.string.eversense_signal_weak)
        strength > 0   -> rh.gs(R.string.eversense_signal_poor)
        else           -> rh.gs(R.string.eversense_not_connected)
    }

    override fun onStateChanged(state: EversenseState) {
        val running = running ?: return   // stopped: a late callback changes nothing
        EversenseLogger.diagnostic { aapsLogger.info(LTag.BGSOURCE, "New state received: ${Json.encodeToString(state)}") }

        // Update sensor battery level for Overview status lights
        sensorBatteryLevel = if (state.batteryPercentage >= 0) state.batteryPercentage else -1

        // Keep SENSOR_CHANGE therapy event in sync with transmitter insertion date.
        // Disabled for E3: the forced overwrite every BT cycle injected raw transmitter
        // uptime (subject to deep-sleep pauses), causing the home screen timer to drift.
        // E365 is unaffected and retains the original sync behavior.
        if (state.insertionDate > 0 && eversense.is365()) {
            running.scope.launch {
                persistenceLayer.insertCgmSourceData(Sources.Eversense, emptyList(), emptyList(), state.insertionDate)
                EversenseLogger.diagnostic { aapsLogger.info(LTag.BGSOURCE, "Updated SENSOR_CHANGE event to insertionDate: ${state.insertionDate}") }
            }
        }

        // Sync SAGE color thresholds to match Eversense sensor lifetime and notification days
        if (state.insertionDate > 0) {
            val lifetimeDays = if (eversense.is365()) 365 else 180
            val warnHours  = (lifetimeDays - 30) * 24   // orange when 30 days remaining
            val urgentHours = (lifetimeDays - 10) * 24  // red when 10 days remaining
            preferences.put(IntKey.OverviewSageWarning, warnHours)
            preferences.put(IntKey.OverviewSageCritical, urgentHours)
        }

        // Check for persistent no-signal — indicates transmitter not placed over sensor
        if (state.sensorSignalStrength == 0) {
            running.consecutiveNoSignalReadings++
            aapsLogger.warn(LTag.BGSOURCE, "No signal reading ${running.consecutiveNoSignalReadings} of $NO_SIGNAL_WARNING_THRESHOLD")
            if (running.consecutiveNoSignalReadings >= NO_SIGNAL_WARNING_THRESHOLD) {
                onTransmitterNotPlaced()
                running.consecutiveNoSignalReadings = 0
            }
        } else {
            running.consecutiveNoSignalReadings = 0
            notificationManager.dismiss(NotificationId.EVERSENSE_PLACEMENT)
        }

        // Show sensor expiry notifications at 60, 30, and 10 days remaining — once each, at noon, keyed to insertionDate
        if (state.insertionDate > 0) {
            val sensorLifetimeMs = if (eversense.is365()) 365L * 24 * 60 * 60 * 1000 else 180L * 24 * 60 * 60 * 1000
            val expiryMs = state.insertionDate + sensorLifetimeMs
            val daysRemaining = ((expiryMs - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
            val isAfterNoon = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= 12

            if (isAfterNoon && daysRemaining in 31..60 && !isSensorExpiryDismissed(state.insertionDate, 60)) {
                setSensorExpiryDismissed(state.insertionDate, 60)
                notificationManager.post(
                    NotificationId.EVERSENSE_ALARM,
                    rh.gs(R.string.eversense_sensor_expiry_plan, daysRemaining),
                    level = NotificationLevel.INFO
                )
            } else if (isAfterNoon && daysRemaining in 11..30 && !isSensorExpiryDismissed(state.insertionDate, 30)) {
                setSensorExpiryDismissed(state.insertionDate, 30)
                notificationManager.post(
                    NotificationId.EVERSENSE_ALARM,
                    rh.gs(R.string.eversense_sensor_expiry_soon, daysRemaining),
                    level = NotificationLevel.NORMAL
                )
            } else if (isAfterNoon && daysRemaining in 1..10 && !isSensorExpiryDismissed(state.insertionDate, daysRemaining)) {
                setSensorExpiryDismissed(state.insertionDate, daysRemaining)
                notificationManager.post(
                    NotificationId.EVERSENSE_ALARM,
                    rh.gs(R.string.eversense_sensor_expiry_urgent, daysRemaining),
                    level = NotificationLevel.URGENT
                )
            }
        }

        // Battery low notification — fires once at noon when battery < 11%
        val isAfterNoonBattery = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= 12
        if (isAfterNoonBattery && state.batteryPercentage in 1..10 && !isBatteryLowDismissed()) {
            setBatteryLowDismissed()
            notificationManager.post(
                NotificationId.EVERSENSE_ALARM,
                rh.gs(R.string.eversense_battery_low, state.batteryPercentage),
                level = NotificationLevel.NORMAL
            )
        }

        // Calibration due notification — fires once per calibration day
        // Round to day boundary so clock drift doesn't cause duplicate notifications
        val calDueDay = (state.nextCalibrationDate / 86400000L) * 86400000L
        if (state.nextCalibrationDate > 0
            && System.currentTimeMillis() >= state.nextCalibrationDate
            && !isCalibrationDueDismissed(calDueDay)) {
            setCalibrationDueDismissed(calDueDay)
            notificationManager.post(
                NotificationId.EVERSENSE_ALARM,
                rh.gs(R.string.eversense_calibration_due),
                level = NotificationLevel.NORMAL
            )
        }

        // Show firmware notification only once per unique firmware version
        if (state.firmwareVersion.isNotEmpty() && state.firmwareVersion != lastNotifiedFirmwareVersion) {
            setLastNotifiedFirmwareVersion(state.firmwareVersion)
            aapsLogger.info(LTag.BGSOURCE, "Transmitter firmware: ${state.firmwareVersion}")
            notificationManager.post(
                NotificationId.EVERSENSE_FIRMWARE,
                rh.gs(R.string.eversense_firmware_update, state.firmwareVersion),
                level = NotificationLevel.INFO
            )
        }
    }

    override fun onTransmitterNotPlaced() {
        aapsLogger.warn(LTag.BGSOURCE, "Transmitter not placed — firing placement warning notification")
        if (running == null) return
        notificationManager.post(
            NotificationId.EVERSENSE_PLACEMENT,
            rh.gs(R.string.eversense_transmitter_not_placed),
            level = NotificationLevel.URGENT
        )
    }

    override fun onConnectionChanged(connected: Boolean) {
        aapsLogger.info(LTag.BGSOURCE, "Connection changed — connected: $connected")
    }

    override fun onTransmitterReady() {
        aapsLogger.info(LTag.BGSOURCE, "Transmitter ready — triggering fullSync")
        // A fresh connection counts as liveness even before the first reading arrives.
        eversense.submitToExecutorAndSync(force = true)
    }


    override fun onAlarmReceived(alarm: ActiveAlarm) {
        aapsLogger.info(LTag.BGSOURCE, "Eversense alarm received: ${alarm.code.title}")
        // CRITICAL_FAULT (code 0) is sent for both hardware faults and calibration-overdue events.
        // If the stored next calibration date has already passed, treat it as a calibration alarm.
        val title = if (alarm.code == EversenseAlarm.CRITICAL_FAULT) {
            val stateJson = securePrefs.getString(StorageKeys.STATE, null)
            val state = stateJson?.let { json.decodeFromString<EversenseState>(it) }
            if (state != null && state.nextCalibrationDate > 0 && state.nextCalibrationDate < System.currentTimeMillis()) {
                rh.gs(R.string.eversense_calibration_due_now)
            } else {
                alarm.code.title
            }
        } else {
            alarm.code.title
        }
        // The transmitter pushes an alarm code with no EversenseAlarm mapping (-> UNKNOWN) within
        // ~1-2s of us exiting diagnostic/positioning mode (confirmed from device logs: signal/
        // battery/calibration all healthy at the time - this is a mode-transition status push, not
        // a real fault). Only suppress within that narrow window, so a genuinely unrecognized alarm
        // arriving at any other time still surfaces as "Unknown Error" rather than being silently
        // dropped.
        val isSpuriousPostExitUnknown = alarm.code == EversenseAlarm.UNKNOWN &&
            System.currentTimeMillis() - eversense.lastPositioningModeExitAt <= POST_EXIT_UNKNOWN_ALARM_SUPPRESS_WINDOW_MS
        if (isSpuriousPostExitUnknown) {
            aapsLogger.info(LTag.BGSOURCE, "Suppressing unrecognized alarm (code ${alarm.codeRaw}) received shortly after exiting positioning mode")
            return
        }
        val level = when {
            alarm.code.isWarning -> NotificationLevel.NORMAL
            alarm.code.isInfo -> NotificationLevel.INFO
            else -> NotificationLevel.URGENT
        }
        if (running == null) return
        notificationManager.post(
            NotificationId.EVERSENSE_ALARM,
            title,
            level = level
        )
    }

    override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {
        val running = running ?: return   // stopped: a reading of the old link is not stored
        // The transmitter is alive — reset the liveness watchdog.
        // Log what the BLE layer actually produced, per reading, before anything downstream can
        // filter or reshape it. The DMS uploader drops readings with no rawResponseHex and keys
        // the portal record on sensorId, and neither field is visible anywhere else in an exported
        // log - the Eversense packet classes log through EversenseLogger, which only reaches
        // logcat. Without this, a reading that never makes it to the portal cannot be told apart
        // from one the server discarded.
        //
        // Backfill readings are the ones to watch: GlucoseHistoryItem carries no sensorId at all,
        // so anything reconstructed from the transmitter's log arrives here with sensorId empty.
        EversenseLogger.diagnostic {
            readings.forEach { r ->
                aapsLogger.info(
                    LTag.BGSOURCE,
                    "Eversense CGM read: ts=${r.datetime} glucose=${r.glucoseInMgDl} " +
                        "rawHexBytes=${r.rawResponseHex.length / 2} sensorId=${if (r.sensorId.isEmpty()) "EMPTY" else r.sensorId}"
                )
            }
        }
        val glucoseValues = readings.map { reading ->
            GV(
                timestamp = reading.datetime,
                value = reading.glucoseInMgDl.toDouble(),
                noise = null,
                raw = null,
                trendArrow = TrendArrow.fromString(reading.trend.type),
                sourceSensor = when (type) {
                    EversenseType.EVERSENSE_365 -> SourceSensor.EVERSENSE_365
                    EversenseType.EVERSENSE_E3  -> SourceSensor.EVERSENSE_E3
                }
            )
        }

        running.scope.launch {
            val state = eversense.getCurrentState()
            val insertionDate = state.insertionDate.takeIf { it > 0 }
            val result = persistenceLayer.insertCgmSourceData(
                Sources.Eversense,
                glucoseValues,
                listOf(),
                insertionDate
            )
            EversenseLogger.diagnostic { aapsLogger.info(LTag.BGSOURCE, "CGM insert complete — inserted: ${result.inserted}, updated: ${result.updated}") }

            if (cloudUploadEnabled()) {
                try {
                    running.cloudUploader.enqueue(type, readings, state)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logCloudError("Eversense cloud queue failed; local readings are stored", e)
                }
            }
        }
    }

    /** The upload of one start: it runs on that start's [scope] and ends with it. The queue itself is stored. */
    private fun newCloudUploader(scope: CoroutineScope) =
        EversenseCloudUploader(
            scope = { scope },
            store = { eversense.cloudStore() },
            enabled = ::cloudUploadEnabled,
            online = { eversense.hasInternet() },
            now = System::currentTimeMillis,
            elapsed = SystemClock::elapsedRealtime,
            upload = ::uploadCloudBatch,
            onError = { logCloudError("Eversense cloud upload failed; local readings are stored", it) }
        )

    private fun logCloudError(message: String, error: Exception) {
        aapsLogger.error(LTag.BGSOURCE, "$message (${error.javaClass.simpleName})")
        EversenseLogger.diagnostic { aapsLogger.error(LTag.BGSOURCE, message, error) }
    }

    private suspend fun uploadCloudBatch(prefs: EversenseStore, batch: EversenseCloudUploader.PendingUpload): Boolean {
        val state = batch.state
        val readings = batch.readings
        val latest = readings.maxByOrNull { it.datetime } ?: return true
        val calibrations = state.calibrationHistory.filter { it.datetime == state.lastCalibrationDate }
        return if (batch.type == EversenseType.EVERSENSE_365) {
            val glucose = EversenseHttp365Util.uploadGlucoseReadings(
                prefs, readings, state.transmitterName.ifEmpty { state.transmitterSerialNumber }, state.firmwareVersion)
            aapsLogger.info(LTag.BGSOURCE, "Eversense hourly upload: success=${glucose.success} sent=${glucose.sentCount} status=${glucose.httpStatus}")
            EversenseLogger.diagnostic { aapsLogger.info(LTag.BGSOURCE, "Eversense hourly upload details: ${glucose.describe()}") }
            if (!glucose.success) return false
            val portal = EversenseHttp365Util.putCurrentValues(
                prefs, latest.glucoseInMgDl, latest.datetime, latest.trend, state.sensorSignalStrength, state.batteryPercentage)
            if (!portal.success) return false
            EversenseHttp365Util.putDeviceEvents(
                prefs, readings, state.transmitterSerialNumber, calibrations, state.activeAlarms).success
        } else {
            val portal = EversenseHttpE3Util.putCurrentValues(
                prefs, latest.glucoseInMgDl, latest.datetime, latest.trend, state.sensorSignalStrength, state.batteryPercentage)
            if (!portal) return false
            EversenseHttpE3Util.putDeviceEvents(
                prefs, readings, state.transmitterSerialNumber, calibrations, state.activeAlarms)
        }
    }
}
