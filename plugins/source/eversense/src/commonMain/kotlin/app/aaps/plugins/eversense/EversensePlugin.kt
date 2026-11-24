package app.aaps.plugins.eversense

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.DosingEffect
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.bluetoothPermissionGroup
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.source.CalibrationMode
import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.interfaces.source.CgmDriver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.core.ui.compose.icons.IcPluginEversense
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.eversense.alerts.EversenseAlertKind
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.cloud.EversenseCloudUploader
import app.aaps.plugins.eversense.di.EversenseCentral
import app.aaps.plugins.eversense.enums.CalibrationReadiness
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.keys.EversenseBooleanKey
import app.aaps.plugins.eversense.keys.EversenseRuntimeKey
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.pairing.EversensePairing
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.storage.edit
import app.aaps.plugins.eversense.ui.EversenseComposeContent
import app.aaps.plugins.eversense.util.EversenseHttp365Util
import app.aaps.plugins.eversense.util.EversenseHttpE3Util
import app.aaps.plugins.eversense.util.StorageKeys
import app.aaps.plugins.source.AbstractBgSourcePlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlin.time.TimeSource

// Registered as a BG source in EversenseRegistration, which each platform with the Eversense driver includes
@SingleIn(AppScope::class)
@Inject
class EversensePlugin(
    override val rh: TextResolver,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
    notificationManager: NotificationManager,
    private val eversenseProvider: () -> EversenseDriver,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    @param:EversenseCentral private val bleCentral: BleCentral,
    private val pairing: EversensePairing,
    // A provider: the alert core reaches the plugins, this one among them
    private val alerts: () -> Alerts
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { _ -> EversenseComposeContent(title = rh.gs(EversenseStrings.source_eversense)) }
        .icon(IcPluginEversense)
        .pluginName(EversenseStrings.source_eversense)
        .preferencesVisibleInSimpleMode(false)
        .description(EversenseStrings.description_source_eversense),
    // The login and the transmitter are not settings: the pairing wizard stores them with the driver, not exported
    ownPreferences = EversenseBooleanKey.entries + EversenseRuntimeKey.entries,
    aapsLogger, rh, preferences, config, notificationManager
), CgmDriver, EversenseWatcher {

    private val eversense get() = eversenseProvider()

    /** Not paired, or the login refused: the bottom bar's BG source button shows a "!" and this screen the fix. */
    override val attention: StateFlow<CgmAttention?> get() = pairing.attention

    companion object {

        // See onAlarmReceived()'s isSpuriousPostExitUnknown comment: the transmitter's unrecognized
        // post-exit status push arrives ~1-2s after exitPositioningMode() per device logs; this is a
        // generous margin over that without being wide enough to risk masking an unrelated
        // genuinely-unrecognized alarm that happens to follow shortly after someone closes the
        // placement guide.
        private const val POST_EXIT_UNKNOWN_ALARM_SUPPRESS_WINDOW_MS = 5000L

        /** The battery low notice fires at this level or below, and is armed again above [BATTERY_REARM_PERCENT]. */
        internal const val BATTERY_LOW_PERCENT = 10
        private const val BATTERY_REARM_PERCENT = 20

        // The keys of the alerts AAPS raises itself; a transmitter alarm's key is its code (keyOf)
        internal const val KEY_PLACEMENT = "placement"
        internal const val KEY_BATTERY_LOW = "battery_low"
        internal const val KEY_CALIBRATION_DUE = "calibration_due"
        internal const val KEY_SENSOR_EXPIRY = "sensor_expiry"

    }


    override var sensorBatteryLevel = -1

    override val sensorLifetimeHours: Int
        get() = eversense.sensorLifetimeDays()?.let { it * 24 } ?: 0

    /**
     * What one start of the plugin owns: its coroutines, the cloud upload in progress and the counters in memory.
     * [onStop] drops it whole, and the next [onStart] builds a new one from storage, so nothing of a stopped run is
     * trusted again. Null while the plugin is stopped: a callback that arrives late finds nothing to work with.
     */
    private class Running(val scope: CoroutineScope, val cloudUploader: EversenseCloudUploader) {

        /** Readings in a row with no sensor signal: three mean the transmitter is not over the sensor. */
        var consecutiveNoSignalReadings = 0

        /**
         * The 365's active alarms at its last sync. An alarm new in the list is raised, and one that left it ends: the
         * 365 says when an alarm ends. The E3 only pushes its alarms, so this stays empty for it.
         */
        val activeAlarms = mutableSetOf<EversenseAlarm>()
    }

    @Volatile private var running: Running? = null

    private val json = Json { ignoreUnknownKeys = true }

    /** The cloud upload measures its hour on a clock the user cannot change */
    private val monotonicStart = TimeSource.Monotonic.markNow()

    private val securePrefs: EversenseStore get() = eversense.preferences

    /** The upload signs in to the Eversense cloud: an E3 used without a login queues nothing. */
    /** An emulated transmitter (the `emulate_eversense` option file): its readings must never reach a real account */
    private val emulated = config.isEnabled(ExternalOptions.EMULATE_EVERSENSE)

    private fun cloudUploadEnabled() = !emulated && preferences.get(EversenseBooleanKey.EversenseCloudUploadEnabled) && pairing.account.value != null

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
    private fun setBatteryLowDismissed(dismissed: Boolean) =
        securePrefs.edit(commit = true) { putBoolean("eversense_battery_low_dismissed", dismissed) }
    private val NO_SIGNAL_WARNING_THRESHOLD = 3
    private val CALIBRATION_RECONNECT_TIMEOUT_MS = 30_000L

    /** Asked for on the app's permission sheet, as a pump driver's; a start never opens a screen of its own. */
    override fun requiredPermissions(): List<PermissionGroup> = listOfNotNull(bluetoothPermissionGroup())

    /**
     * Loads everything from storage and connects: the driver reads the login, the transmitter and its keys from its
     * own store, and connects when a transmitter is paired. A start after a stop is a fresh start: [Running] is new,
     * and so are the driver's counters.
     */
    override suspend fun onStart() {
        super.onStart()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        running = Running(scope, newCloudUploader(scope))
        eversense.addWatcher(this)
        eversense.start()
        // Unpaired: the transmitter's alerts are of a transmitter that is gone
        pairing.attention.filter { it == CgmAttention.NOT_PAIRED }
            .onEach { alerts().resolveAll(EversenseAlertKind.SOURCE) }
            .launchIn(scope)
        // Android can leave out the disconnect callback while Bluetooth is off: reconnect when it is back on. The
        // current state is skipped, the start above has just connected.
        bleCentral.adapterState.drop(1).filter { it == AdapterState.ON }
            .onEach {
                aapsLogger.info(LTag.BGSOURCE, "Bluetooth turned back on — forcing Eversense reconnect")
                eversense.onBluetoothAvailable()
            }
            .launchIn(scope)
    }

    /**
     * Drops everything [onStart] built: the coroutines with the cloud upload in progress, the link and its retries
     * (the driver's stop), the counters, and the transmitter's alerts - once stopped, nothing could end them. Readings
     * and the upload queue are stored and stay.
     */
    override suspend fun onStop() {
        super.onStop()
        running?.scope?.cancel()
        running = null
        eversense.removeWatcher(this)
        eversense.stop()
        sensorBatteryLevel = -1
        alerts().resolveAll(EversenseAlertKind.SOURCE)
    }

    // ── CgmDriver ── The user enters the finger-stick value in the AAPS calibration dialog; the master's
    // record writer hands it to calibrate() below.

    override val calibrationMode = CalibrationMode.SOURCE

    /** The values the transmitter takes as a calibration: the dialog and the master's check offer only these */
    override val calibrationRangeMgdl: ClosedFloatingPointRange<Double> = 40.0..400.0

    // Without a transmitter there is nothing to send to. Said before the confirmation, not after a wait for a link
    // that cannot come. A refused login does not stop it: the value goes to the transmitter over Bluetooth.
    override suspend fun calibrationRefusal(): String? =
        if (pairing.attention.value == CgmAttention.NOT_PAIRED) rh.gs(EversenseStrings.eversense_not_paired)
        else readinessRefusal(eversense.getCurrentState().calibrationReadiness)?.let { rh.gs(it) }

    /**
     * What the transmitter said at its last sync about taking a calibration. Only the E3 says it; a 365 always reads
     * as ready. Unknown (not read yet) is left to the transmitter.
     */
    private fun readinessRefusal(readiness: CalibrationReadiness): TextRef? = when (readiness) {
        CalibrationReadiness.READY,
        CalibrationReadiness.REASON_UNKNOWN           -> null
        CalibrationReadiness.GLUCOSE_RATE_TOO_HIGH    -> EversenseStrings.eversense_calibration_refused_rate
        CalibrationReadiness.TOO_SOON,
        CalibrationReadiness.WAITING_POST_CALIBRATION -> EversenseStrings.eversense_calibration_refused_too_soon
        CalibrationReadiness.NOT_ENOUGH_DATA,
        CalibrationReadiness.DROPOUT_PHASE            -> EversenseStrings.eversense_calibration_refused_no_data
        CalibrationReadiness.SENSOR_EOL,
        CalibrationReadiness.NO_SENSOR_LINKED         -> EversenseStrings.eversense_calibration_refused_sensor
        CalibrationReadiness.UNSUPPORTED_MODE,
        CalibrationReadiness.LED_DISCONNECT_DETECTED,
        CalibrationReadiness.TRANSMITTER_EOL          -> EversenseStrings.eversense_calibration_refused_transmitter
    }

    /** The transmitter is connected first when it is not; the confirmation says so, since that can take a while. */
    override fun calibrationNote(): String? =
        if (eversense.isConnected()) null else rh.gs(EversenseStrings.eversense_calibration_note_reconnect)

    override suspend fun calibrate(bgMgdl: Double): String? = withContext(Dispatchers.IO) {
        calibrationRefusal()?.let { return@withContext it }
        // Rounded, not truncated: 2.2 mmol/L is 39.6 mg/dL and must not become 39
        val value = bgMgdl.roundToInt()
        if (value.toDouble() !in calibrationRangeMgdl) return@withContext rh.gs(EversenseStrings.eversense_calibration_invalid_value)
        if (!eversense.isConnected()) {
            aapsLogger.info(LTag.BGSOURCE, "Eversense calibration: not connected — reconnecting first")
            val reconnected = withTimeoutOrNull(CALIBRATION_RECONNECT_TIMEOUT_MS) { awaitTransmitterReady() }
            if (reconnected == null) {
                aapsLogger.warn(LTag.BGSOURCE, "Eversense calibration: reconnect timed out")
                return@withContext rh.gs(EversenseStrings.eversense_calibration_failed)
            }
        }
        val success = eversense.sendCalibration(value)
        aapsLogger.info(LTag.BGSOURCE, "Eversense calibration result: $success")
        if (!success) return@withContext rh.gs(EversenseStrings.eversense_calibration_failed)
        eversense.triggerFullSync(force = true)
        // Drives the home-screen "Calibration in progress" countdown banner.
        preferences.put(LongNonKey.EversenseLastCalibrationSubmittedAt, dateUtil.now())
        null
    }

    /** Connects and waits until the transmitter is ready; the caller limits the wait. */
    private suspend fun awaitTransmitterReady() {
        val ready = CompletableDeferred<Unit>()
        val watcher = object : EversenseWatcher {
            override fun onTransmitterReady() {
                ready.complete(Unit)
            }

            override fun onConnectionChanged(connected: Boolean) {}
            override fun onStateChanged(state: EversenseState) {}
            override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {}
            override fun onAlarmReceived(alarm: ActiveAlarm) {}
            override fun onTransmitterNotPlaced() {}
        }
        eversense.addWatcher(watcher)
        try {
            if (eversense.isConnected()) return
            eversense.connect()
            ready.await()
        } finally {
            eversense.removeWatcher(watcher)
        }
    }

    // Pairing, status, calibration and placement are on the plugin's own screen (EversenseComposeContent), opened from
    // Manage as a pump's screen is. The login is the pairing wizard's, not a setting.
    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "eversense_settings",
        title = EversenseStrings.source_eversense,
        items = listOf(
            EversenseBooleanKey.EversenseCloudUploadEnabled
        ),
        icon = pluginDescription.icon
    )

    override fun onStateChanged(state: EversenseState) {
        val running = running ?: return   // stopped: a late callback changes nothing
        aapsLogger.debug(LTag.BGSOURCE) { "New state received: ${Json.encodeToString(state)}" }

        // Update sensor battery level for Overview status lights
        sensorBatteryLevel = if (state.batteryPercentage >= 0) state.batteryPercentage else -1

        // Keep SENSOR_CHANGE therapy event in sync with transmitter insertion date.
        // Disabled for E3: the forced overwrite every BT cycle injected raw transmitter
        // uptime (subject to deep-sleep pauses), causing the home screen timer to drift.
        // E365 is unaffected and retains the original sync behavior.
        if (state.insertionDate > 0 && eversense.is365()) {
            running.scope.launch {
                persistenceLayer.insertCgmSourceData(Sources.Eversense, emptyList(), emptyList(), state.insertionDate)
                aapsLogger.debug(LTag.BGSOURCE) { "Updated SENSOR_CHANGE event to insertionDate: ${state.insertionDate}" }
            }
        }

        // The sensor's lifetime sets the sensor age limits on every phone. Written only when it changes: it syncs out.
        sensorLifetimeHours.let { hours -> if (preferences.get(IntNonKey.SensorLifetimeHours) != hours) preferences.put(IntNonKey.SensorLifetimeHours, hours) }

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
            resolve(KEY_PLACEMENT)
        }

        // Sensor expiry at 60, 30, and 10 days remaining — once each, at noon, keyed to insertionDate. Not dosing: the
        // sensor works until its end, which the transmitter says itself (Sensor replacement).
        if (state.insertionDate > 0) {
            val sensorLifetimeMs = if (eversense.is365()) 365L * 24 * 60 * 60 * 1000 else 180L * 24 * 60 * 60 * 1000
            val expiryMs = state.insertionDate + sensorLifetimeMs
            val daysRemaining = ((expiryMs - dateUtil.now()) / (24 * 60 * 60 * 1000)).toInt()
            val isAfterNoon = Instant.fromEpochMilliseconds(dateUtil.now()).toLocalDateTime(TimeZone.currentSystemDefault()).hour >= 12

            if (isAfterNoon && daysRemaining in 31..60 && !isSensorExpiryDismissed(state.insertionDate, 60)) {
                setSensorExpiryDismissed(state.insertionDate, 60)
                raiseSensorExpiry(DosingEffect.INFO, EversenseStrings.eversense_sensor_expiry_plan.withArgs(daysRemaining))
            } else if (isAfterNoon && daysRemaining in 11..30 && !isSensorExpiryDismissed(state.insertionDate, 30)) {
                setSensorExpiryDismissed(state.insertionDate, 30)
                raiseSensorExpiry(DosingEffect.INFO, EversenseStrings.eversense_sensor_expiry_soon.withArgs(daysRemaining))
            } else if (isAfterNoon && daysRemaining in 1..10 && !isSensorExpiryDismissed(state.insertionDate, daysRemaining)) {
                setSensorExpiryDismissed(state.insertionDate, daysRemaining)
                // The user has to get a new sensor in these days: one chime
                raiseSensorExpiry(DosingEffect.ACTION, EversenseStrings.eversense_sensor_expiry_urgent.withArgs(daysRemaining))
            }
        }

        // Battery low: once per discharge. The transmitter is recharged often, so the alert is armed again when the
        // battery is back above the limit. At any hour: an empty transmitter gives no readings, so dosing soon.
        if (state.batteryPercentage in 1..BATTERY_LOW_PERCENT && !isBatteryLowDismissed()) {
            setBatteryLowDismissed(true)
            raise(
                key = KEY_BATTERY_LOW, alarm = EversenseAlarm.VERY_LOW_BATTERY, effect = DosingEffect.SOON,
                text = EversenseStrings.eversense_battery_low.withArgs(state.batteryPercentage), lifetime = AlertLifetime.EVENT
            )
        } else if (state.batteryPercentage > BATTERY_REARM_PERCENT && isBatteryLowDismissed()) {
            setBatteryLowDismissed(false)
            resolve(KEY_BATTERY_LOW)
        }

        // Calibration due — once per calibration day. Rounded to the day so clock drift does not raise it twice.
        // Dosing soon: without a calibration the transmitter stops giving readings.
        val calDueDay = (state.nextCalibrationDate / 86400000L) * 86400000L
        if (state.nextCalibrationDate > 0
            && dateUtil.now() >= state.nextCalibrationDate
            && !isCalibrationDueDismissed(calDueDay)) {
            setCalibrationDueDismissed(calDueDay)
            raise(
                key = KEY_CALIBRATION_DUE, alarm = EversenseAlarm.CALIBRATION_NOW, effect = DosingEffect.SOON,
                text = EversenseStrings.eversense_calibration_due, lifetime = AlertLifetime.EVENT
            )
        }

        // The 365 lists its active alarms at each sync, so an alarm that left the list has ended
        if (eversense.is365()) {
            val active = state.activeAlarms.map { it.code }.toSet()
            (active - running.activeAlarms).forEach { onTransmitterAlarm(it) }
            (running.activeAlarms - active).forEach { resolve(keyOf(it)) }
            running.activeAlarms.clear()
            running.activeAlarms.addAll(active)
        }

        // Show firmware notification only once per unique firmware version
        if (state.firmwareVersion.isNotEmpty() && state.firmwareVersion != lastNotifiedFirmwareVersion) {
            setLastNotifiedFirmwareVersion(state.firmwareVersion)
            aapsLogger.info(LTag.BGSOURCE, "Transmitter firmware: ${state.firmwareVersion}")
            notificationManager.post(
                NotificationId.EVERSENSE_FIRMWARE,
                rh.gs(EversenseStrings.eversense_firmware_update, state.firmwareVersion),
                level = NotificationLevel.INFO
            )
        }
    }

    /** No readings while it is so: dosing now. Ends when the signal is back. */
    override fun onTransmitterNotPlaced() {
        aapsLogger.warn(LTag.BGSOURCE, "Transmitter not placed — raising the placement alert")
        if (running == null) return
        raise(
            key = KEY_PLACEMENT, kind = EversenseAlertKind.PLACEMENT, title = EversenseStrings.eversense_placement_title,
            effect = DosingEffect.NOW, text = EversenseStrings.eversense_transmitter_not_placed, lifetime = AlertLifetime.CONDITION
        )
    }

    override fun onConnectionChanged(connected: Boolean) {
        aapsLogger.info(LTag.BGSOURCE, "Connection changed — connected: $connected")
    }

    // The driver syncs a new link by itself
    override fun onTransmitterReady() {
        aapsLogger.info(LTag.BGSOURCE, "Transmitter ready")
    }


    override fun onAlarmReceived(alarm: ActiveAlarm) {
        aapsLogger.info(LTag.BGSOURCE, "Eversense alarm received: ${alarm.code.name}")
        // The transmitter pushes an alarm code with no EversenseAlarm mapping (-> UNKNOWN) within
        // ~1-2s of us exiting diagnostic/positioning mode (confirmed from device logs: signal/
        // battery/calibration all healthy at the time - this is a mode-transition status push, not
        // a real fault). Only suppress within that narrow window, so a genuinely unrecognized alarm
        // arriving at any other time still surfaces as "Unknown Error" rather than being silently
        // dropped.
        val isSpuriousPostExitUnknown = alarm.code == EversenseAlarm.UNKNOWN &&
            dateUtil.now() - eversense.lastPositioningModeExitAt <= POST_EXIT_UNKNOWN_ALARM_SUPPRESS_WINDOW_MS
        if (isSpuriousPostExitUnknown) {
            aapsLogger.info(LTag.BGSOURCE, "Suppressing unrecognized alarm (code ${alarm.codeRaw}) received shortly after exiting positioning mode")
            return
        }
        val running = running ?: return
        // On the 365 the next sync lists it too: kept here, so that list does not raise it a second time
        if (eversense.is365()) running.activeAlarms.add(alarm.code)
        onTransmitterAlarm(alarm.code)
    }

    /**
     * A transmitter alarm as an alert (`_docs/CGM_DESIGN.md`, section 5). AAPS holds the transmitter link, so the
     * vendor app is not there to sound it. One alert per alarm: a repeat updates its own alert, and a second alarm does
     * not silence the first. A glucose alarm raises nothing: AAPS's own BG alarms raise those from the readings.
     *
     * CRITICAL_FAULT (code 0) is sent both for a hardware fault and for a calibration that is overdue. When the next
     * calibration date has passed, it is shown as the calibration alarm.
     */
    private fun onTransmitterAlarm(code: EversenseAlarm) {
        if (code.effect == DosingEffect.GLUCOSE) {
            aapsLogger.info(LTag.BGSOURCE, "Eversense glucose alarm ${code.name}: left to the BG alarms of AAPS")
            return
        }
        val calibrationOverdue = code == EversenseAlarm.CRITICAL_FAULT && calibrationOverdue()
        val shownAs = if (calibrationOverdue) EversenseAlarm.CALIBRATION_REQUIRED else code
        // The 365 says when an alarm ends, so one that stops dosing lasts until then. The rest end with the user's answer.
        val lifetime = if (shownAs.effect == DosingEffect.NOW && eversense.is365()) AlertLifetime.CONDITION else AlertLifetime.EVENT
        raise(
            key = keyOf(code), alarm = shownAs, effect = shownAs.effect,
            text = if (calibrationOverdue) EversenseStrings.eversense_calibration_due_now else null, lifetime = lifetime
        )
    }

    private fun calibrationOverdue(): Boolean {
        val state = securePrefs.getString(StorageKeys.STATE, null)?.let { json.decodeFromString<EversenseState>(it) }
        return state != null && state.nextCalibrationDate > 0 && state.nextCalibrationDate < dateUtil.now()
    }

    /** The key of a transmitter alarm's alert: its own code, also when it is shown as another alarm. */
    private fun keyOf(code: EversenseAlarm) = "alarm.${code.name}"

    /** Raises the alert of one of the transmitter's alarms, shown as [alarm]: its kind and its title. */
    private fun raise(key: String, alarm: EversenseAlarm, effect: DosingEffect, text: TextRef?, lifetime: AlertLifetime) {
        val kind = EversenseAlertKind.of(alarm) ?: return
        raise(key, kind, alarm.title, effect, text, lifetime)
    }

    private fun raiseSensorExpiry(effect: DosingEffect, text: TextRef) =
        raise(KEY_SENSOR_EXPIRY, EversenseAlarm.SENSOR_RETIRING_SOON_1, effect, text, AlertLifetime.EVENT)

    /**
     * Raises an alert of the transmitter. While one with the same key is active it keeps that one's time, so the same
     * alarm again updates it and does not ring again; a silence given to it still holds.
     */
    private fun raise(key: String, kind: EversenseAlertKind, title: TextRef, effect: DosingEffect, text: TextRef?, lifetime: AlertLifetime) {
        val severity = effect.severity ?: return
        val alerts = alerts()
        val since = alerts.active.value.firstOrNull { it.source == EversenseAlertKind.SOURCE && it.key == key }?.raisedAt ?: dateUtil.now()
        alerts.raise(Alert(kind = kind, key = key, severity = severity, title = title, text = text, raisedAt = since, lifetime = lifetime))
    }

    private fun resolve(key: String) = alerts().resolve(EversenseAlertKind.SOURCE, key)

    override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {
        val running = running ?: return   // stopped: a reading of the old link is not stored
        // What the transmitter gave, before anything filters it. The cloud upload drops readings with no raw data and
        // the portal keys a record on the sensor id, so a reading that never reaches the portal can be told apart from
        // one the server dropped. Backfill readings have no sensor id.
        readings.forEach { r ->
            aapsLogger.debug(LTag.BGSOURCE) {
                "Eversense CGM read: ts=${r.datetime} glucose=${r.glucoseInMgDl} " +
                    "rawHexBytes=${r.rawResponseHex.length / 2} sensorId=${if (r.sensorId.isEmpty()) "EMPTY" else r.sensorId}"
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
            // As in onStateChanged: only a 365 gives a stable insertion time. Each new E3 value would add a sensor change.
            val insertionDate = state.insertionDate.takeIf { it > 0 && type == EversenseType.EVERSENSE_365 }
            val result = persistenceLayer.insertCgmSourceData(
                Sources.Eversense,
                glucoseValues,
                listOf(),
                insertionDate
            )
            aapsLogger.debug(LTag.BGSOURCE) { "CGM insert complete — inserted: ${result.inserted}, updated: ${result.updated}" }

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
            now = dateUtil::now,
            elapsed = { monotonicStart.elapsedNow().inWholeMilliseconds },
            upload = ::uploadCloudBatch,
            onError = { logCloudError("Eversense cloud upload failed; local readings are stored", it) }
        )

    private fun logCloudError(message: String, error: Exception) {
        aapsLogger.error(LTag.BGSOURCE, message, error)
    }

    private suspend fun uploadCloudBatch(prefs: EversenseStore, batch: EversenseCloudUploader.PendingUpload): Boolean {
        val state = batch.state
        val readings = batch.readings
        val latest = readings.maxByOrNull { it.datetime } ?: return true
        val calibrations = state.calibrationHistory.filter { it.datetime == state.lastCalibrationDate }
        return if (batch.type == EversenseType.EVERSENSE_365) {
            val glucose = EversenseHttp365Util.uploadGlucoseReadings(
                aapsLogger, prefs, readings, state.transmitterName.ifEmpty { state.transmitterSerialNumber }, state.firmwareVersion)
            aapsLogger.info(LTag.BGSOURCE, "Eversense hourly upload: success=${glucose.success} sent=${glucose.sentCount} status=${glucose.httpStatus}")
            aapsLogger.debug(LTag.BGSOURCE) { "Eversense hourly upload details: ${glucose.describe()}" }
            if (!glucose.success) return false
            val portal = EversenseHttp365Util.putCurrentValues(
                aapsLogger, prefs, latest.glucoseInMgDl, latest.datetime, latest.trend, state.sensorSignalStrength, state.batteryPercentage)
            if (!portal.success) return false
            EversenseHttp365Util.putDeviceEvents(
                aapsLogger, prefs, readings, state.transmitterSerialNumber, calibrations, state.activeAlarms).success
        } else {
            val portal = EversenseHttpE3Util.putCurrentValues(
                aapsLogger, prefs, latest.glucoseInMgDl, latest.datetime, latest.trend, state.sensorSignalStrength, state.batteryPercentage)
            if (!portal) return false
            EversenseHttpE3Util.putDeviceEvents(
                aapsLogger, prefs, readings, state.transmitterSerialNumber, calibrations, state.activeAlarms)
        }
    }
}
