package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.ui.compose.icons.IcPluginByoda
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.source.activities.RequestDexcomPermissionActivity
import app.aaps.plugins.source.compose.BgSourceComposeContent
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DexcomPlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val context: Context,
    config: Config,
    preferences: Preferences,
) : AbstractBgSourceWithSensorInsertLogPlugin(
    pluginDescription = PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { plugin ->
            BgSourceComposeContent(
                title = rh.gs(R.string.dexcom_app_patched)
            )
        }
        .icon(IcPluginByoda)
        .pluginName(R.string.dexcom_app_patched)
        .shortName(R.string.dexcom_short)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_dexcom),
    aapsLogger = aapsLogger,
    rh = rh,
    preferences = preferences
), BgSource, DexcomBoyda {

    init {
        if (!config.AAPSCLIENT) {
            pluginDescription.setDefault()
        }
    }

    override fun requiredPermissions(): List<PermissionGroup> =
        if (isDexcomAppInstalled()) listOf(
            PermissionGroup(
                permissions = listOf(PERMISSION),
                rationaleTitle = R.string.permission_dexcom_title,
                rationaleDescription = R.string.permission_dexcom_description,
                special = true,
            )
        ) else emptyList()

    private fun isDexcomAppInstalled(): Boolean =
        PACKAGE_NAMES.any { pkg ->
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }

    override fun onStart() {
        super.onStart()
        requestPermissionIfNeeded()
    }

    // cannot be inner class because of needed injection
    class DexcomWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var dexcomPlugin: DexcomPlugin
        @Inject lateinit var preferences: Preferences
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var dataWorkerStorage: DataWorkerStorage
        @Inject lateinit var persistenceLayer: PersistenceLayer
        @Inject lateinit var profileUtil: ProfileUtil

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!dexcomPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val bundle = dataWorkerStorage.pickupBundle(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))
            try {
                val sourceSensor = when (bundle.getString("sensorType") ?: "") {
                    "G6" -> SourceSensor.DEXCOM_G6_NATIVE
                    "G7" -> SourceSensor.DEXCOM_G7_NATIVE
                    else -> SourceSensor.DEXCOM_NATIVE_UNKNOWN
                }
                val calibrations = mutableListOf<PersistenceLayer.Calibration>()
                bundle.getBundle("meters")?.let { meters ->
                    for (i in 0 until meters.size()) {
                        meters.getBundle(i.toString())?.let {
                            val timestamp = it.getLong("timestamp") * 1000
                            val now = dateUtil.now()
                            val value = it.getInt("meterValue").toDouble()
                            if (timestamp > now - T.months(1).msecs() && timestamp < now) {
                                calibrations.add(
                                    PersistenceLayer.Calibration(
                                        timestamp = it.getLong("timestamp") * 1000,
                                        value = value,
                                        glucoseUnit = profileUtil.unitsDetect(value)
                                    )
                                )
                            }
                        }
                    }
                }
                val now = dateUtil.now()
                val glucoseValuesBundle = bundle.getBundle("glucoseValues")
                    ?: return Result.failure(workDataOf("Error" to "missing glucoseValues"))
                val glucoseValues = mutableListOf<GV>()
                for (i in 0 until glucoseValuesBundle.size()) {
                    val glucoseValueBundle = glucoseValuesBundle.getBundle(i.toString())!!
                    val timestamp = glucoseValueBundle.getLong("timestamp") * 1000
                    // G5 calibration bug workaround (calibration is sent as glucoseValue too)
                    var valid = true
                    // G6 is sending one 24h old changed value causing recalculation. Ignore
                    if (sourceSensor == SourceSensor.DEXCOM_G6_NATIVE)
                        if ((now - timestamp) > T.hours(20).msecs()) valid = false
                    if (valid)
                        glucoseValues += GV(
                            timestamp = timestamp,
                            value = glucoseValueBundle.getInt("glucoseValue").toDouble(),
                            noise = null,
                            raw = null,
                            trendArrow = TrendArrow.fromString(glucoseValueBundle.getString("trendArrow")!!),
                            sourceSensor = sourceSensor
                        )
                }
                var sensorStartTime = if (preferences.get(BooleanKey.BgSourceCreateSensorChange) && bundle.containsKey("sensorInsertionTime")) {
                    bundle.getLong("sensorInsertionTime", 0) * 1000
                } else {
                    null
                }
                // check start time validity
                sensorStartTime?.let {
                    if (abs(it - now) > T.months(1).msecs() || it > now) sensorStartTime = null
                }
                val result = try {
                    persistenceLayer.insertCgmSourceData(Sources.Dexcom, glucoseValues, calibrations, sensorStartTime)
                } catch (e: Exception) {
                    ret = Result.failure(workDataOf("Error" to e.toString()))
                    null
                }
                result?.let {
                    // G6 calibration bug workaround (2 additional GVs are created within 1 minute)
                    for (i in it.inserted.indices) {
                        if (sourceSensor == SourceSensor.DEXCOM_G6_NATIVE) {
                            if (i < it.inserted.size - 1) {
                                if (abs(it.inserted[i].timestamp - it.inserted[i + 1].timestamp) < T.mins(1).msecs()) {
                                    persistenceLayer.invalidateGlucoseValue(it.inserted[i].id, Action.BG_REMOVED, Sources.Dexcom, note = null, listValues = listOf())
                                    persistenceLayer.invalidateGlucoseValue(it.inserted[i + 1].id, Action.BG_REMOVED, Sources.Dexcom, note = null, listValues = listOf())
                                    it.inserted.removeAt(i + 1)
                                    it.inserted.removeAt(i)
                                    continue
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                aapsLogger.error("Error while processing intent from Dexcom App", e)
                ret = Result.failure(workDataOf("Error" to e.toString()))
            }
            return ret
        }
    }

    override fun requestPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(context, RequestDexcomPermissionActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun dexcomPackages() = PACKAGE_NAMES

    companion object {

        private val PACKAGE_NAMES = listOf(
            "com.dexcom.g6.region1.mmol", "com.dexcom.g6.region2.mgdl",
            "com.dexcom.g6.region3.mgdl", "com.dexcom.g6.region3.mmol",
            "com.dexcom.g6", "com.dexcom.g7"
        )
        const val PERMISSION = "com.dexcom.cgm.EXTERNAL_PERMISSION"
    }
}
