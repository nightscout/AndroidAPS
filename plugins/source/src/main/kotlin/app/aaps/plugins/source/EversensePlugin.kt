package app.aaps.plugins.source

import android.content.Context
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
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
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import com.nightscout.eversense.EversenseCGMPlugin
import com.nightscout.eversense.callbacks.EversenseScanCallback
import com.nightscout.eversense.callbacks.EversenseWatcher
import com.nightscout.eversense.enums.CalibrationReadiness
import com.nightscout.eversense.enums.EversenseType
import com.nightscout.eversense.models.EversenseCGMResult
import com.nightscout.eversense.models.EversenseScanResult
import com.nightscout.eversense.models.EversenseState
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class EversensePlugin @Inject constructor(
    rh: ResourceHelper,
    private val context: Context,
    aapsLogger: AAPSLogger,
    preferences: Preferences
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        // Fix: Explicitly point to the core object R file
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_blooddrop_48)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .pluginName(R.string.source_eversense)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_eversense),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences
), BgSource, EversenseWatcher {

    @Inject lateinit var persistenceLayer: PersistenceLayer

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private var connectedPreference: Preference? = null
    private var batteryPreference: Preference? = null
    private var insertionPreference: Preference? = null
    private var lastSyncPreference: Preference? = null
    private var currentPhasePreference: Preference? = null
    private var lastCalibrationPreference: Preference? = null
    private var nextCalibrationPreference: Preference? = null
    private var calibrationActionPrefernce: Preference? = null

    // No extra overrides needed; the abstract class handles defaults.
    init {
        EversenseCGMPlugin.instance.setContext(context, true)
        EversenseCGMPlugin.instance.addWatcher(this)

        EversenseCGMPlugin.instance.connect(null)
    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        val state = EversenseCGMPlugin.instance.getCurrentState() ?:run { return }

        val bgSource = PreferenceCategory(context)
        parent.addPreference(bgSource)
        bgSource.apply {
            key = "bg_source_upload_settings"
            title = rh.gs(R.string.bgsource_settings)
            initialExpandedChildrenCount = 0

            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.BgSourceUploadToNs, title = app.aaps.core.ui.R.string.do_ns_upload_title))

            val eselSmoothing = SwitchPreference(context)
            eselSmoothing.key = "eversense_use_smoothing"
            eselSmoothing.title = rh.gs(R.string.eversense_use_smoothing)
            eselSmoothing.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                EversenseCGMPlugin.instance.setSmoothing(newValue as Boolean)
                true
            }
            addPreference(eselSmoothing)
        }

        val calibration = PreferenceCategory(context)
        parent.addPreference(calibration)
        calibration.apply {
            title = rh.gs(R.string.eversense_calibration_title)

            val currentPhase = Preference(context)
            currentPhase.key = "eversense_calibration_phase"
            currentPhase.title = rh.gs(R.string.eversense_calibration_phase)
            currentPhase.summary = state.calibrationPhase.name
            addPreference(currentPhase)
            currentPhasePreference = currentPhase

            val lastCalibration = Preference(context)
            lastCalibration.key = "eversense_calibration_last"
            lastCalibration.title = rh.gs(R.string.eversense_calibration_last)
            lastCalibration.summary = dateFormatter.format(Date(state.lastCalibrationDate))
            addPreference(lastCalibration)
            lastCalibrationPreference = lastCalibration

            val nextCalibration = Preference(context)
            nextCalibration.key = "eversense_calibration_next"
            nextCalibration.title = rh.gs(R.string.eversense_calibration_next)
            nextCalibration.summary = dateFormatter.format(Date(state.nextCalibrationDate))
            addPreference(nextCalibration)
            nextCalibrationPreference = nextCalibration

            val calibrationAction = Preference(context)
            calibrationAction.key = "eversense_calibration_action"
            calibrationAction.title = rh.gs(R.string.eversense_calibration_action)
            calibrationAction.summary = if (state.calibrationReadiness != CalibrationReadiness.READY) state.calibrationReadiness.name else ""
            calibrationAction.isEnabled = state.calibrationReadiness == CalibrationReadiness.READY
            calibrationAction.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val state = EversenseCGMPlugin.instance.getCurrentState() ?:run { return@OnPreferenceClickListener false }
                if (state.calibrationReadiness != CalibrationReadiness.READY) {
                    return@OnPreferenceClickListener false
                }

                // TODO: Do navigation to calibration screen
                return@OnPreferenceClickListener true
            }
            addPreference(calibrationAction)
            calibrationActionPrefernce = calibrationAction
        }

        val information = PreferenceCategory(context)
        parent.addPreference(information)
        information.apply {
            title = rh.gs(R.string.eversense_information_title)

            val connected = Preference(context)
            connected.key = "eversense_information_connected"
            connected.title = rh.gs(R.string.eversense_information_connected)
            connected.summary = if (EversenseCGMPlugin.instance.isConnected()) "✅" else "❌"
            connected.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                Log.d("TEST", "ONCLICK")
                val scanning = object : EversenseScanCallback {
                    override fun onResult(item: EversenseScanResult) {
                        EversenseCGMPlugin.instance.connect(item.device)
                    }
                }
                EversenseCGMPlugin.instance.startScan(scanning)
                return@OnPreferenceClickListener true
            }
            addPreference(connected)
            connectedPreference = connected

            val battery = Preference(context)
            battery.key = "eversense_information_battery"
            battery.title = rh.gs(R.string.eversense_information_battery)
            battery.summary = "${state.batteryPercentage}%"
            addPreference(battery)
            batteryPreference = battery

            val insertion = Preference(context)
            insertion.key = "eversense_information_insertion_date"
            insertion.title = rh.gs(R.string.eversense_information_insertion_date)
            insertion.summary = dateFormatter.format(Date(state.insertionDate))
            addPreference(insertion)
            insertionPreference = insertion

            val lastSync = Preference(context)
            lastSync.key = "eversense_information_last_sync"
            lastSync.title = rh.gs(R.string.eversense_information_last_sync)
            lastSync.summary = dateFormatter.format(Date(state.lastSync))
            addPreference(lastSync)
            lastSyncPreference = lastSync
        }
    }

    override fun onStateChanged(state: EversenseState) {
        aapsLogger.info(LTag.BGSOURCE, "New state received: ${Json.encodeToString(state)}")

        batteryPreference?.let { it.summary = "${state.batteryPercentage}%" }
        insertionPreference?.let { it.summary = dateFormatter.format(Date(state.insertionDate)) }
        lastSyncPreference?.let { it.summary = dateFormatter.format(Date(state.lastSync)) }

        currentPhasePreference?.let { it.summary = state.calibrationPhase.name }
        lastCalibrationPreference?.let { it.summary = dateFormatter.format(Date(state.lastCalibrationDate)) }
        nextCalibrationPreference?.let { it.summary = dateFormatter.format(Date(state.nextCalibrationDate)) }
        calibrationActionPrefernce?.let {
            it.summary = if (state.calibrationReadiness != CalibrationReadiness.READY) state.calibrationReadiness.name else ""
            it.isEnabled = state.calibrationReadiness == CalibrationReadiness.READY
        }
    }

    override fun onConnectionChanged(connected: Boolean) {
        aapsLogger.info(LTag.BGSOURCE, "Connection changed - connected: $connected")

        connectedPreference?.let { it.summary = if (connected) "✅" else "❌" }
    }

    override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {
        val glucoseValues = mutableListOf<GV>()

        for (reading in readings) {
            glucoseValues += GV(
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

        val result = persistenceLayer.insertCgmSourceData(
            Sources.Eversense,
            glucoseValues,
            listOf(),
            null
        ).blockingGet()
    }
}
