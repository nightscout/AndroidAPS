package app.aaps.plugins.source

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.eversense.callbacks.EversenseScanCallback
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.source.compose.BgSourceComposeContent
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseScanResult
import app.aaps.plugins.eversense.models.EversenseSecureState
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.util.StorageKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import app.aaps.core.keys.interfaces.withActivity
import app.aaps.core.keys.interfaces.withClick
import app.aaps.plugins.source.activities.EversenseStatusActivity
import app.aaps.plugins.source.activities.EversenseCalibrationActivity
import app.aaps.plugins.source.activities.EversensePlacementActivity
import app.aaps.plugins.source.keys.EversenseIntentKey
import app.aaps.plugins.source.keys.EversenseStringKey
import app.aaps.core.ui.compose.icons.IcPluginEversense
import app.aaps.core.keys.BooleanKey
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import javax.inject.Inject

class EversensePlugin @Inject constructor(
    rh: ResourceHelper,
    private val context: Context,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
    private val notificationManager: NotificationManager,
    private val eversense: EversenseCGMPlugin
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { _ ->
            BgSourceComposeContent(
                title = rh.gs(R.string.source_eversense)
            )
        }
        .icon(IcPluginEversense)
        .pluginName(R.string.source_eversense)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_eversense),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences, config
), BgSource, EversenseWatcher {

    @Inject lateinit var persistenceLayer: PersistenceLayer

    override var sensorBatteryLevel = -1

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // connectGatt() calls made while the Bluetooth radio is off never fire any callback, so the
    // timer-based reconnect backoff in EversenseGattCallback can silently retry into a dead radio
    // and never recover. This receiver re-triggers a reconnect the moment the adapter is confirmed
    // back on, regardless of what those timers happened to do in the meantime. Same pattern as
    // RileyLinkBluetoothStateReceiver. Uses forceReconnect() rather than connect() because Android
    // also doesn't reliably deliver a disconnect callback when the radio powers off in the first
    // place, so EversenseGattCallback's "connected" flag may be stale true here.
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON) {
                aapsLogger.info(LTag.BGSOURCE, "Bluetooth turned back on — forcing Eversense reconnect")
                ioScope.launch {
                    eversense.forceReconnect()
                }
            }
        }
    }
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val json = Json { ignoreUnknownKeys = true }

    private val securePrefs: SharedPreferences get() = eversense.preferences

    private fun cloudUploadEnabled() = preferences.get(BooleanKey.EversenseCloudUploadEnabled)

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
    private var consecutiveNoSignalReadings: Int = 0
    private val NO_SIGNAL_WARNING_THRESHOLD = 3
    private var releaseForOfficialApp: Boolean = false
    @Volatile private var placementNotificationSnoozed: Boolean = false

    override suspend fun onStart() {
        super.onStart()
        ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        eversense.addWatcher(this)
        context.registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        if (hasBluetoothPermissions()) {
            aapsLogger.debug(LTag.BGSOURCE, "onStart — permissions granted, attempting auto-reconnect")
            ioScope.launch {
                eversense.connect(null)
            }
        } else {
            aapsLogger.warn(LTag.BGSOURCE, "Bluetooth permissions not granted — requesting permissions")
            requestBluetoothPermissions()
        }
        // Always sync credentials on startup — eversense.is365() is false until first connect
        // so we must set username/password unconditionally for new phone first-boot
        checkCredentialsNotification()
    }

    override suspend fun onStop() {
        super.onStop()
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        eversense.removeWatcher(this)
        try {
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: IllegalArgumentException) {
            // Not registered (e.g. onStart never completed registration) — safe to ignore
        }
    }

    private fun requestBluetoothPermissions() {
        val intent = Intent(context, app.aaps.plugins.source.activities.RequestEversensePermissionActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun syncCredentialsIfNeeded() = checkCredentialsNotification()

    private fun checkCredentialsNotification() {
        val username = preferences.get(EversenseStringKey.EversenseUsername)
        val password = preferences.get(EversenseStringKey.EversensePassword)
        if (username.isEmpty() || password.isEmpty()) {
            notificationManager.post(
                NotificationId.EVERSENSE_CREDENTIALS,
                rh.gs(R.string.eversense_credentials_missing),
                level = NotificationLevel.URGENT
            )
        } else {
            // Sync credentials into SECURE_STATE immediately — fixes new phone deadlock
            // where login fails before first glucose reading so SECURE_STATE never gets populated
            val secureState = getSecureState()
            val credentialsChanged = secureState.username != username || secureState.password != password
            secureState.username = username
            secureState.password = password
            saveSecureState(secureState)
            // Also set on EversenseCGMPlugin so GattCallback can sync before login
            eversense.username = username
            eversense.password = password
            if (credentialsChanged) {
                securePrefs.edit(commit = true) {
                    remove(app.aaps.plugins.eversense.util.StorageKeys.ACCESS_TOKEN)
                    remove(app.aaps.plugins.eversense.util.StorageKeys.ACCESS_TOKEN_EXPIRY)
                }
                aapsLogger.info(LTag.BGSOURCE, "Eversense: credentials synced to secure state")
            }
            notificationManager.dismiss(NotificationId.EVERSENSE_CREDENTIALS)
        }
    }

    private fun getSecureState(): EversenseSecureState {
        val stateJson = securePrefs.getString(StorageKeys.SECURE_STATE, null) ?: "{}"
        return json.decodeFromString(stateJson)
    }

    private fun saveSecureState(state: EversenseSecureState) {
        securePrefs.edit(commit = true) {
            putString(StorageKeys.SECURE_STATE, json.encodeToString(EversenseSecureState.serializer(), state))
        }
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "eversense_settings",
        titleResId = R.string.source_eversense,
        items = listOf(
            EversenseIntentKey.EversenseStatus.withActivity(EversenseStatusActivity::class.java),
            BooleanKey.EversenseCloudUploadEnabled,
            PreferenceSubScreenDef(
                key = "eversense_credentials_screen",
                titleResId = R.string.eversense_credentials_title,
                items = listOf(
                    EversenseStringKey.EversenseUsername,
                    EversenseStringKey.EversensePassword
                )
            ),
            EversenseIntentKey.EversenseSignOut.withClick {
                val cleared = getSecureState().also {
                    it.username = ""
                    it.password = ""
                }
                saveSecureState(cleared)
                aapsLogger.info(LTag.BGSOURCE, "Eversense credentials cleared by user")
            },
            EversenseIntentKey.EversenseCalibration.withActivity(EversenseCalibrationActivity::class.java as Class<*>),
            EversenseIntentKey.EversensePlacement.withActivity(EversensePlacementActivity::class.java as Class<*>)
        ),
        icon = pluginDescription.icon
    )

    private fun startOfficialAppReleaseReconnectLoop() {
        if (!releaseForOfficialApp) return
        aapsLogger.info(LTag.BGSOURCE, "Release mode — attempting reconnect")
        ioScope.launch {
            eversense.connect(null)
            mainHandler.postDelayed({
                if (eversense.isConnected()) {
                    aapsLogger.info(LTag.BGSOURCE, "Reconnected after official app release")
                    releaseForOfficialApp = false
                    mainHandler.post {
                        notificationManager.dismiss(NotificationId.EVERSENSE_RELEASE)
                    }
                } else {
                    aapsLogger.info(LTag.BGSOURCE, "Reconnect failed — retrying in 5 minutes")
                    mainHandler.postDelayed({ startOfficialAppReleaseReconnectLoop() }, 300000L)
                }
            }, 10000L)
        }
    }

    private fun signalToLabel(strength: Int): String = when {
        strength >= 75 -> rh.gs(R.string.eversense_signal_excellent)
        strength >= 48 -> rh.gs(R.string.eversense_signal_good)
        strength >= 30 -> rh.gs(R.string.eversense_signal_fair)
        strength >= 25 -> rh.gs(R.string.eversense_signal_weak)
        strength > 0   -> rh.gs(R.string.eversense_signal_poor)
        else           -> rh.gs(R.string.eversense_not_connected)
    }

    override fun onStateChanged(state: EversenseState) {
        aapsLogger.info(LTag.BGSOURCE, "New state received: ${Json.encodeToString(state)}")

        // Update sensor battery level for Overview status lights
        sensorBatteryLevel = if (state.batteryPercentage >= 0) state.batteryPercentage else -1

        // Keep SENSOR_CHANGE therapy event in sync with transmitter insertion date.
        // Disabled for E3: the forced overwrite every BT cycle injected raw transmitter
        // uptime (subject to deep-sleep pauses), causing the home screen timer to drift.
        // E365 is unaffected and retains the original sync behavior.
        if (state.insertionDate > 0 && eversense.is365()) {
            ioScope.launch {
                persistenceLayer.insertCgmSourceData(Sources.Eversense, emptyList(), emptyList(), state.insertionDate)
                aapsLogger.debug(LTag.BGSOURCE, "Updated SENSOR_CHANGE event to insertionDate: ${state.insertionDate}")
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
            consecutiveNoSignalReadings++
            aapsLogger.warn(LTag.BGSOURCE, "No signal reading $consecutiveNoSignalReadings of $NO_SIGNAL_WARNING_THRESHOLD")
            if (consecutiveNoSignalReadings >= NO_SIGNAL_WARNING_THRESHOLD) {
                if (!placementNotificationSnoozed) {
                    onTransmitterNotPlaced()
                }
                consecutiveNoSignalReadings = 0
            }
        } else {
            consecutiveNoSignalReadings = 0
            placementNotificationSnoozed = false
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
        mainHandler.post {
            notificationManager.post(
                NotificationId.EVERSENSE_PLACEMENT,
                rh.gs(R.string.eversense_transmitter_not_placed),
                level = NotificationLevel.URGENT
            )
        }
    }

    override fun onConnectionChanged(connected: Boolean) {
        aapsLogger.info(LTag.BGSOURCE, "Connection changed — connected: $connected")
    }

    override fun onTransmitterReady() {
        aapsLogger.info(LTag.BGSOURCE, "Transmitter ready — triggering fullSync")
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
        val level = when {
            alarm.code.isWarning -> NotificationLevel.NORMAL
            alarm.code.isInfo -> NotificationLevel.INFO
            else -> NotificationLevel.URGENT
        }
        mainHandler.post {
            notificationManager.post(
                NotificationId.EVERSENSE_ALARM,
                title,
                level = level
            )
        }
    }

    override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {
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

        ioScope.launch {
            val state = eversense.getCurrentState()
            val insertionDate = state.insertionDate.takeIf { it > 0 }
            val result = persistenceLayer.insertCgmSourceData(
                Sources.Eversense,
                glucoseValues,
                listOf(),
                insertionDate
            )
            aapsLogger.info(LTag.BGSOURCE, "CGM insert complete — inserted: ${result.inserted}, updated: ${result.updated}")

            // Upload readings to Eversense cloud so official app sees data without needing BLE
            if ((type == EversenseType.EVERSENSE_365 || type == EversenseType.EVERSENSE_E3) && cloudUploadEnabled()) {
                val prefs = eversense.preferences
                // Sync credentials from AAPS preferences into SECURE_STATE so EversenseHttp365Util can read them
                val username = preferences.get(EversenseStringKey.EversenseUsername)
                val password = preferences.get(EversenseStringKey.EversensePassword)
                if (username.isNotEmpty() && password.isNotEmpty()) {
                    val secureState = getSecureState()
                    // Only invalidate the token cache if credentials have actually changed.
                    val credentialsChanged = secureState.username != username || secureState.password != password
                    secureState.username = username
                    secureState.password = password
                    saveSecureState(secureState)
                    if (credentialsChanged) {
                        val prefs2 = eversense.preferences
                        prefs2.edit(commit = true) {
                            remove(app.aaps.plugins.eversense.util.StorageKeys.ACCESS_TOKEN)
                            remove(app.aaps.plugins.eversense.util.StorageKeys.ACCESS_TOKEN_EXPIRY)
                        }
                        aapsLogger.info(LTag.BGSOURCE, "Eversense: credentials changed — token cache cleared, will re-login on next upload")
                    }
                    notificationManager.dismiss(NotificationId.EVERSENSE_CREDENTIALS)
                } else {
                    notificationManager.post(
                        NotificationId.EVERSENSE_CREDENTIALS,
                        rh.gs(R.string.eversense_credentials_missing),
                        level = NotificationLevel.URGENT
                    )
                }

                if (type == EversenseType.EVERSENSE_365) {
                    // E365 US upload
                    val uploadOk = try {
                        app.aaps.plugins.eversense.util.EversenseHttp365Util.uploadGlucoseReadings(
                            preferences = prefs,
                            readings = readings,
                            transmitterSerialNumber = state.transmitterName.ifEmpty { state.transmitterSerialNumber },
                            firmwareVersion = state.firmwareVersion
                        )
                    } catch (e: Exception) {
                        aapsLogger.error(LTag.BGSOURCE, "Eversense uploadGlucoseReadings EXCEPTION: ", e)
                        false
                    }
                    val msg365 = if (uploadOk)
                        "Eversense cloud upload: ✅ ${readings.size} reading(s) sent"
                    else
                        "Eversense cloud upload: ❌ failed — check credentials and internet"
                    aapsLogger.info(LTag.BGSOURCE, msg365)
                    // Cloud-upload failures are logged only, not toasted - a routine BLE hiccup
                    // (e.g. a disconnect mid-sync) retries within seconds, and toasting every
                    // failed attempt was spamming the user during sustained connection trouble.

                    val latest = readings.firstOrNull { it.rawResponseHex.isNotEmpty() } ?: readings.firstOrNull()
                    if (latest != null) {
                        val portalOk = app.aaps.plugins.eversense.util.EversenseHttp365Util.putCurrentValues(
                            preferences = prefs,
                            glucose = latest.glucoseInMgDl,
                            timestamp = latest.datetime,
                            trend = latest.trend,
                            signalStrength = state.sensorSignalStrength,
                            batteryPercentage = state.batteryPercentage
                        )
                        aapsLogger.info(LTag.BGSOURCE, "Eversense portal sync: ${if (portalOk) "✅ ok" else "❌ failed"}")
                    }

                    val uploadableReadings = readings.filter { it.rawResponseHex.isNotEmpty() }
                    if (uploadableReadings.isNotEmpty()) {
                        val eventsOk = app.aaps.plugins.eversense.util.EversenseHttp365Util.putDeviceEvents(
                            preferences = prefs,
                            readings = uploadableReadings,
                            transmitterSerialNumber = state.transmitterSerialNumber,
                            calibrations = state.calibrationHistory.filter { it.datetime == state.lastCalibrationDate },
                            alerts = state.activeAlarms
                        )
                        aapsLogger.info(LTag.BGSOURCE, "Eversense device events: ${if (eventsOk) "✅ ok" else "❌ failed"}")
                    }
                } else {
                    // E3 EU/OUS upload
                    val latest = readings.firstOrNull()
                    if (latest != null) {
                        val portalOk = app.aaps.plugins.eversense.util.EversenseHttpE3Util.putCurrentValues(
                            preferences = prefs,
                            glucose = latest.glucoseInMgDl,
                            timestamp = latest.datetime,
                            trend = latest.trend,
                            signalStrength = state.sensorSignalStrength,
                            batteryPercentage = state.batteryPercentage
                        )
                        aapsLogger.info(LTag.BGSOURCE, "E3 portal sync: ${if (portalOk) "✅ ok" else "❌ failed"}")
                    }
                    val eventsOk = app.aaps.plugins.eversense.util.EversenseHttpE3Util.putDeviceEvents(
                        preferences = prefs,
                        readings = readings,
                        transmitterSerialNumber = state.transmitterSerialNumber,
                        calibrations = state.calibrationHistory.filter { it.datetime == state.lastCalibrationDate },
                        alerts = state.activeAlarms
                    )
                    val msgE3 = if (eventsOk)
                        "E3 cloud upload: ✅ ${readings.size} reading(s) sent"
                    else
                        "E3 cloud upload: ❌ failed — check credentials and internet"
                    aapsLogger.info(LTag.BGSOURCE, msgE3)
                    // Cloud-upload failures are logged only, not toasted - see the 365 path above.
                }
            }
        }
    }

    private fun showDeviceSelectionDialog(context: Context) {
        val foundDevices = mutableListOf<EversenseScanResult>()
        var isCancelled = false
        var dialog: AlertDialog? = null

        val scanCallback = object : EversenseScanCallback {
            override fun onResult(item: EversenseScanResult) {
                val isEversenseTransmitter = item.name.matches(Regex("T\\d+.*"))
                if (!isCancelled && isEversenseTransmitter && foundDevices.none { it.name == item.name }) {
                    foundDevices.add(item)
                    aapsLogger.info(LTag.BGSOURCE, "Scan found device: ${item.name}")
                }
            }
        }

        eversense.startScan(scanCallback)

        mainHandler.postDelayed({
            if (isCancelled) return@postDelayed
            eversense.stopScan()
            dialog?.dismiss()

            if (foundDevices.isEmpty()) {
                AlertDialog.Builder(context)
                    .setTitle(rh.gs(R.string.eversense_scan_title))
                    .setMessage(rh.gs(R.string.eversense_no_transmitters_found))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } else {
                val items = foundDevices.map { it.name }.toTypedArray()
                AlertDialog.Builder(context)
                    .setTitle(rh.gs(R.string.eversense_scan_title))
                    .setItems(items) { _, position ->
                        val selected = foundDevices[position]
                        aapsLogger.info(LTag.BGSOURCE, "User selected device: ${selected.name}")
                        eversense.connect(selected.device)
                    }
                    .setNegativeButton(rh.gs(R.string.eversense_scan_cancel), null)
                    .show()
            }
        }, 10000)

        dialog = AlertDialog.Builder(context)
            .setTitle(rh.gs(R.string.eversense_scan_title))
            .setMessage(rh.gs(R.string.eversense_scan_in_progress))
            .setNegativeButton(rh.gs(R.string.eversense_scan_cancel)) { _, _ ->
                isCancelled = true
                eversense.stopScan()
            }
            .setCancelable(false)
            .show()
    }

}
