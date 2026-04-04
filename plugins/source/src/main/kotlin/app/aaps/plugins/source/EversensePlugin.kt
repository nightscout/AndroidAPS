package app.aaps.plugins.source

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.interfaces.Preferences
import com.nightscout.eversense.EversenseCGMPlugin
import com.nightscout.eversense.callbacks.EversenseScanCallback
import com.nightscout.eversense.callbacks.EversenseWatcher
import com.nightscout.eversense.enums.CalibrationReadiness
import com.nightscout.eversense.enums.EversenseType
import com.nightscout.eversense.models.ActiveAlarm
import com.nightscout.eversense.models.EversenseCGMResult
import com.nightscout.eversense.models.EversenseScanResult
import com.nightscout.eversense.models.EversenseSecureState
import com.nightscout.eversense.models.EversenseState
import com.nightscout.eversense.util.StorageKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EversensePlugin @Inject constructor(
    rh: ResourceHelper,
    private val context: Context,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
    private val notificationManager: NotificationManager
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_blooddrop_48)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .pluginName(R.string.source_eversense)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_eversense),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences, config
), BgSource, EversenseWatcher {

    @Inject lateinit var persistenceLayer: PersistenceLayer
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val json = Json { ignoreUnknownKeys = true }
    private val securePrefs by lazy {
        context.getSharedPreferences("EversenseCGMManager", Context.MODE_PRIVATE)
    }
    private var connectedPreference: Preference? = null
    private var releasePreference: Preference? = null
    private var lastNotifiedFirmwareVersion: String = ""
    private var consecutiveNoSignalReadings: Int = 0
    private val NO_SIGNAL_WARNING_THRESHOLD = 3

    private val eversense get() = EversenseCGMPlugin.instance

    override fun onStart() {
        super.onStart()
        eversense.setContext(context, true)
        eversense.addWatcher(this)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            eversense.connect(null)
        }
    }

    override fun onStop() {
        super.onStop()
        eversense.removeWatcher(this)
    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        val eselSmoothing = SwitchPreference(context)
        eselSmoothing.key = "eversense_use_smoothing"
        eselSmoothing.title = rh.gs(R.string.eversense_use_smoothing)
        eselSmoothing.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            eversense.setSmoothing(newValue as Boolean)
            true
        }

        val credentialsCategory = PreferenceCategory(context)
        credentialsCategory.title = rh.gs(R.string.eversense_credentials_title)

        val username = EditTextPreference(context)
        username.key = "eversense_credentials_username"
        username.title = rh.gs(R.string.eversense_credentials_username)
        val secureState = getSecureState()
        username.summary = if (secureState.username.isNotEmpty()) secureState.username
        else rh.gs(R.string.eversense_credentials_not_set)
        username.dialogTitle = rh.gs(R.string.eversense_credentials_username)
        username.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
            val state = getSecureState()
            val updated = EversenseSecureState().also { it.canUseShortcut = state.canUseShortcut; it.username = newValue as String; it.password = state.password; it.clientId = state.clientId; it.privateKey = state.privateKey; it.publicKey = state.publicKey }
            saveSecureState(updated)
            val newStr = newValue as String
            pref.summary = if (newStr.isNotEmpty()) newStr else rh.gs(R.string.eversense_credentials_not_set)
            aapsLogger.info(LTag.BGSOURCE, "Eversense username updated")
            true
        }

        val password = EditTextPreference(context)
        password.key = "eversense_credentials_password"
        password.title = rh.gs(R.string.eversense_credentials_password)
        password.summary = if (secureState.password.isNotEmpty()) rh.gs(R.string.eversense_credentials_password_set)
        else rh.gs(R.string.eversense_credentials_not_set)
        password.dialogTitle = rh.gs(R.string.eversense_credentials_password)
        password.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
            val state = getSecureState()
            val updated = EversenseSecureState().also { it.canUseShortcut = state.canUseShortcut; it.username = state.username; it.password = newValue as String; it.clientId = state.clientId; it.privateKey = state.privateKey; it.publicKey = state.publicKey }
            saveSecureState(updated)
            pref.summary = if ((newValue as String).isNotEmpty()) rh.gs(R.string.eversense_credentials_password_set)
            else rh.gs(R.string.eversense_credentials_not_set)
            aapsLogger.info(LTag.BGSOURCE, "Eversense password updated")
            true
        }

        val signOut = Preference(context)
        signOut.key = "eversense_credentials_sign_out"
        signOut.title = "Sign out"
        signOut.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            AlertDialog.Builder(context)
                .setTitle("Sign out")
                .setMessage("Are you sure you want to clear your Eversense credentials?")
                .setPositiveButton("Yes") { _, _ ->
                    saveSecureState(EversenseSecureState())
                    username.summary = rh.gs(R.string.eversense_credentials_not_set)
                    password.summary = rh.gs(R.string.eversense_credentials_not_set)
                    aapsLogger.info(LTag.BGSOURCE, "Eversense credentials cleared by user")
                }
                .setNegativeButton("No", null)
                .show()
            true
        }

        val calibrationCategory = PreferenceCategory(context)
        calibrationCategory.title = rh.gs(R.string.eversense_calibration_title)

        val currentPhase = Preference(context)
        currentPhase.key = "eversense_calibration_phase"
        currentPhase.title = rh.gs(R.string.eversense_calibration_phase)
        val state = eversense.getCurrentState()
        currentPhase.summary = state?.calibrationPhase?.name ?: rh.gs(R.string.eversense_not_connected)

        val lastCalibration = Preference(context)
        lastCalibration.key = "eversense_calibration_last"
        lastCalibration.title = rh.gs(R.string.eversense_calibration_last)
        lastCalibration.summary = if (state != null && state.lastCalibrationDate > 0)
            dateFormatter.format(Date(state.lastCalibrationDate))
        else rh.gs(R.string.eversense_not_connected)

        val nextCalibration = Preference(context)
        nextCalibration.key = "eversense_calibration_next"
        nextCalibration.title = rh.gs(R.string.eversense_calibration_next)
        nextCalibration.summary = if (state != null && state.nextCalibrationDate > 0)
            dateFormatter.format(Date(state.nextCalibrationDate))
        else rh.gs(R.string.eversense_not_connected)

        val calibrationAction = Preference(context)
        calibrationAction.key = "eversense_calibration_action"
        calibrationAction.title = rh.gs(R.string.eversense_calibration_action)
        calibrationAction.summary = when {
            state == null -> rh.gs(R.string.eversense_not_connected)
            state.calibrationReadiness == CalibrationReadiness.TOO_SOON ->
                "⏳ " + rh.gs(R.string.eversense_calibration_too_soon)
            state.calibrationReadiness != CalibrationReadiness.READY -> state.calibrationReadiness.name
            else -> ""
        }
        calibrationAction.isEnabled = state?.calibrationReadiness == CalibrationReadiness.READY
        calibrationAction.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val latestState = eversense.getCurrentState()
            if (latestState?.calibrationReadiness == CalibrationReadiness.READY) {
                val intent = Intent(context, app.aaps.plugins.source.activities.EversenseCalibrationActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                AlertDialog.Builder(context)
                    .setTitle(rh.gs(R.string.eversense_calibration_title))
                    .setMessage(rh.gs(R.string.eversense_calibration_not_supported))
                    .setPositiveButton("OK", null)
                    .show()
            }
            true
        }

        val informationCategory = PreferenceCategory(context)
        informationCategory.title = rh.gs(R.string.eversense_information_title)

        val connected = Preference(context)
        connected.key = "eversense_information_connected"
        connected.title = rh.gs(R.string.eversense_information_connected)
        connected.summary = if (eversense.isConnected()) "\u2705" else "\u274C"
        connected.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            if (eversense.isConnected()) {
                showDeviceScanDialog(context)
            } else {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent(context, app.aaps.plugins.source.activities.RequestEversensePermissionActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } else {
                    showDeviceScanDialog(context)
                }
            }
            true
        }
        connectedPreference = connected

        val battery = Preference(context)
        battery.key = "eversense_information_battery"
        battery.title = rh.gs(R.string.eversense_information_battery)
        battery.summary = if (state != null && state.batteryPercentage >= 0) "${state.batteryPercentage}%" else rh.gs(R.string.eversense_not_connected)

        val placementSignal = Preference(context)
        placementSignal.key = "eversense_information_placement_signal"
        placementSignal.title = rh.gs(R.string.eversense_placement_signal)
        placementSignal.summary = if (state != null && state.sensorSignalStrength > 0) "${state.sensorSignalStrength}%" else rh.gs(R.string.eversense_not_connected)
        placementSignal.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val intent = Intent(context, app.aaps.plugins.source.activities.EversensePlacementActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }

        val insertion = Preference(context)
        insertion.key = "eversense_information_insertion_date"
        insertion.title = rh.gs(R.string.eversense_information_insertion_date)
        insertion.summary = if (state != null && state.insertionDate > 0) dateFormatter.format(Date(state.insertionDate)) else rh.gs(R.string.eversense_not_connected)

        val lastSync = Preference(context)
        lastSync.key = "eversense_information_last_sync"
        lastSync.title = rh.gs(R.string.eversense_information_last_sync)
        lastSync.summary = if (state != null && state.lastSync > 0) dateFormatter.format(Date(state.lastSync)) else rh.gs(R.string.eversense_not_connected)
        lastSync.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            if (!eversense.isConnected()) {
                aapsLogger.warn(LTag.BGSOURCE, "Cannot trigger sync — not connected")
            } else {
                eversense.triggerFullSync(force = true)
            }
            true
        }

        val releaseForApp = Preference(context)
        releaseForApp.key = "eversense_release_for_official_app"
        releaseForApp.title = rh.gs(R.string.eversense_release_for_official_app)
        releaseForApp.summary = rh.gs(R.string.eversense_release_summary)
        releaseForApp.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            eversense.disconnect()
            releaseForApp.summary = rh.gs(R.string.eversense_release_active)
            notificationManager.post(NotificationId.EVERSENSE_RELEASE, "Eversense transmitter released — open the official Eversense app to connect", NotificationLevel.INFO)
            val handler = Handler(Looper.getMainLooper())
            val reconnectRunnable = object : Runnable {
                override fun run() {
                    if (!eversense.isConnected()) {
                        eversense.connect(null)
                        if (!eversense.isConnected()) {
                            handler.postDelayed(this, 300000L)
                        } else {
                            releasePreference?.summary = rh.gs(R.string.eversense_release_summary)
                        }
                    } else {
                        releasePreference?.summary = rh.gs(R.string.eversense_release_summary)
                    }
                }
            }
            handler.postDelayed(reconnectRunnable, 300000L)
            true
        }
        releasePreference = releaseForApp

        parent.addPreference(eselSmoothing)
        parent.addPreference(credentialsCategory)
        credentialsCategory.addPreference(username)
        credentialsCategory.addPreference(password)
        credentialsCategory.addPreference(signOut)
        parent.addPreference(calibrationCategory)
        calibrationCategory.addPreference(currentPhase)
        calibrationCategory.addPreference(lastCalibration)
        calibrationCategory.addPreference(nextCalibration)
        calibrationCategory.addPreference(calibrationAction)
        parent.addPreference(informationCategory)
        informationCategory.addPreference(connected)
        informationCategory.addPreference(battery)
        informationCategory.addPreference(placementSignal)
        informationCategory.addPreference(insertion)
        informationCategory.addPreference(lastSync)
        informationCategory.addPreference(releaseForApp)
    }

    private fun getSecureState(): EversenseSecureState {
        val json = Json { ignoreUnknownKeys = true }
        val stored = securePrefs.getString(StorageKeys.SECURE_STATE, null)
        return if (stored != null) {
            try { json.decodeFromString(EversenseSecureState.serializer(), stored) }
            catch (e: Exception) { EversenseSecureState() }
        } else EversenseSecureState()
    }

    private fun saveSecureState(state: EversenseSecureState) {
        val json = Json { ignoreUnknownKeys = true }
        securePrefs.edit {
            putString(StorageKeys.SECURE_STATE, json.encodeToString(EversenseSecureState.serializer(), state))
        }
    }

    private fun showDeviceScanDialog(context: Context) {
        val foundDevices = mutableListOf<EversenseScanResult>()
        val scanCallback = object : EversenseScanCallback {
            override fun onResult(item: EversenseScanResult) {
                if (foundDevices.none { it.device.address == item.device.address }) {
                    foundDevices.add(item)
                }
            }
        }
        eversense.startScan(scanCallback)
        mainHandler.postDelayed({
            eversense.stopScan()
            mainHandler.post {
                if (foundDevices.isEmpty()) {
                    AlertDialog.Builder(context)
                        .setTitle(rh.gs(R.string.eversense_scan_title))
                        .setMessage("No Eversense transmitters found. Make sure the transmitter is nearby and try again.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    val names = foundDevices.map { it.name ?: rh.gs(R.string.eversense_scan_unknown_device) }.toTypedArray()
                    AlertDialog.Builder(context)
                        .setTitle(rh.gs(R.string.eversense_scan_title))
                        .setItems(names) { _, which ->
                            val selected = foundDevices[which]
                            eversense.connect(selected.device)
                        }
                        .setNegativeButton(rh.gs(R.string.eversense_scan_cancel), null)
                        .show()
                }
            }
        }, 5000L)
        AlertDialog.Builder(context)
            .setTitle(rh.gs(R.string.eversense_scan_title))
            .setMessage("Scanning for Eversense devices...")
            .setNegativeButton(rh.gs(R.string.eversense_scan_cancel)) { _, _ -> eversense.stopScan() }
            .show()
    }

    override fun onTransmitterNotPlaced() {
        aapsLogger.warn(LTag.BGSOURCE, "Transmitter not placed — firing placement warning notification")
        mainHandler.post {
            notificationManager.post(
                id = NotificationId.EVERSENSE_PLACEMENT,
                text = rh.gs(R.string.eversense_transmitter_not_placed),
                level = NotificationLevel.URGENT
            )
        }
    }

    override fun onStateChanged(state: EversenseState) {
        mainHandler.post {
            connectedPreference?.summary = if (eversense.isConnected()) "\u2705" else "\u274C"
        }

        if (state.sensorSignalStrength == 0) {
            consecutiveNoSignalReadings++
            aapsLogger.warn(LTag.BGSOURCE, "No signal reading $consecutiveNoSignalReadings of $NO_SIGNAL_WARNING_THRESHOLD")
            if (consecutiveNoSignalReadings >= NO_SIGNAL_WARNING_THRESHOLD) {
                onTransmitterNotPlaced()
                consecutiveNoSignalReadings = 0
            }
        } else {
            consecutiveNoSignalReadings = 0
            notificationManager.dismiss(NotificationId.EVERSENSE_PLACEMENT)
        }

        if (state.firmwareVersion.isNotEmpty() && state.firmwareVersion != lastNotifiedFirmwareVersion) {
            lastNotifiedFirmwareVersion = state.firmwareVersion
            notificationManager.post(
                id = NotificationId.EVERSENSE_FIRMWARE,
                text = "Eversense firmware: ${state.firmwareVersion} — open the official Eversense app to check for updates",
                level = NotificationLevel.INFO
            )
        }
    }

    override fun onConnectionChanged(connected: Boolean) {
        aapsLogger.info(LTag.BGSOURCE, "Connection changed — connected: $connected")
        mainHandler.post {
            connectedPreference?.summary = if (connected) "\u2705" else "\u274C"
        }
    }

    override fun onAlarmReceived(alarm: ActiveAlarm) {
        aapsLogger.info(LTag.BGSOURCE, "Eversense alarm received: ${alarm.code.title}")
        val level = when {
            alarm.code.isCritical -> NotificationLevel.URGENT
            alarm.code.isWarning  -> NotificationLevel.NORMAL
            else                  -> NotificationLevel.INFO
        }
        mainHandler.post {
            notificationManager.post(
                id = NotificationId.EVERSENSE_ALARM,
                text = alarm.code.title,
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
        if (glucoseValues.isNotEmpty()) {
            ioScope.launch {
                persistenceLayer.insertCgmSourceData(Sources.Eversense, glucoseValues, emptyList(), null)
            }
        }
    }
}


