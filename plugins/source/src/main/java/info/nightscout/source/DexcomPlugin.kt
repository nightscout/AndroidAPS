package info.nightscout.source

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.fromConstant
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CgmSourceTransaction
import info.nightscout.database.impl.transactions.InvalidateGlucoseValueTransaction
import info.nightscout.database.transactions.TransactionGlucoseValue
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.source.BgSource
import info.nightscout.interfaces.source.DexcomBoyda
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.safeGetInstalledPackages
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import info.nightscout.source.activities.RequestDexcomPermissionActivity
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DexcomPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP,
    private val context: Context,
    config: Config
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(info.nightscout.core.main.R.drawable.ic_dexcom_g6)
        .pluginName(R.string.dexcom_app_patched)
        .shortName(R.string.dexcom_short)
        .preferencesId(R.xml.pref_dexcom)
        .description(R.string.description_source_dexcom),
    aapsLogger, rh, injector
), BgSource, DexcomBoyda {

    init {
        if (!config.NSCLIENT) {
            pluginDescription.setDefault()
        }
    }

    override fun advancedFilteringSupported(): Boolean = true

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        (glucoseValue.sourceSensor == GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE ||
            glucoseValue.sourceSensor == GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE ||
            glucoseValue.sourceSensor == GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN)
            && sp.getBoolean(info.nightscout.core.utils.R.string.key_do_ns_upload, false)

    override fun onStart() {
        super.onStart()
        requestPermissionIfNeeded()
    }

    // cannot be inner class because of needed injection
    class DexcomWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var dexcomPlugin: DexcomPlugin
        @Inject lateinit var sp: SP
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var dataWorkerStorage: DataWorkerStorage
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var uel: UserEntryLogger

        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!dexcomPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val bundle = dataWorkerStorage.pickupBundle(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))
            try {
                val sourceSensor = when (bundle.getString("sensorType") ?: "") {
                    "G6" -> GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE
                    "G5" -> GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE
                    else -> GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN
                }
                val calibrations = mutableListOf<CgmSourceTransaction.Calibration>()
                bundle.getBundle("meters")?.let { meters ->
                    for (i in 0 until meters.size()) {
                        meters.getBundle(i.toString())?.let {
                            val timestamp = it.getLong("timestamp") * 1000
                            val now = dateUtil.now()
                            val value = it.getInt("meterValue").toDouble()
                            if (timestamp > now - T.months(1).msecs() && timestamp < now) {
                                calibrations.add(
                                    CgmSourceTransaction.Calibration(
                                        timestamp = it.getLong("timestamp") * 1000,
                                        value = value,
                                        glucoseUnit = TherapyEvent.GlucoseUnit.fromConstant(Profile.unit(value))
                                    )
                                )
                            }
                        }
                    }
                }
                val now = dateUtil.now()
                val glucoseValuesBundle = bundle.getBundle("glucoseValues")
                    ?: return Result.failure(workDataOf("Error" to "missing glucoseValues"))
                val glucoseValues = mutableListOf<TransactionGlucoseValue>()
                for (i in 0 until glucoseValuesBundle.size()) {
                    val glucoseValueBundle = glucoseValuesBundle.getBundle(i.toString())!!
                    val timestamp = glucoseValueBundle.getLong("timestamp") * 1000
                    // G5 calibration bug workaround (calibration is sent as glucoseValue too)
                    var valid = true
                    if (sourceSensor == GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE)
                        calibrations.forEach { calibration -> if (calibration.timestamp == timestamp) valid = false }
                    // G6 is sending one 24h old changed value causing recalculation. Ignore
                    if (sourceSensor == GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE)
                        if ((now - timestamp) > T.hours(20).msecs()) valid = false
                    if (valid)
                        glucoseValues += TransactionGlucoseValue(
                            timestamp = timestamp,
                            value = glucoseValueBundle.getInt("glucoseValue").toDouble(),
                            noise = null,
                            raw = null,
                            trendArrow = GlucoseValue.TrendArrow.fromString(glucoseValueBundle.getString("trendArrow")!!),
                            sourceSensor = sourceSensor
                        )
                }
                var sensorStartTime = if (sp.getBoolean(R.string.key_dexcom_log_ns_sensor_change, false) && bundle.containsKey("sensorInsertionTime")) {
                    bundle.getLong("sensorInsertionTime", 0) * 1000
                } else {
                    null
                }
                // check start time validity
                sensorStartTime?.let {
                    if (abs(it - now) > T.months(1).msecs() || it > now) sensorStartTime = null
                }
                repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, calibrations, sensorStartTime))
                    .doOnError {
                        aapsLogger.error(LTag.DATABASE, "Error while saving values from Dexcom App", it)
                        ret = Result.failure(workDataOf("Error" to it.toString()))
                    }
                    .blockingGet()
                    .also { result ->
                        // G6 calibration bug workaround (2 additional GVs are created within 1 minute)
                        for (i in result.inserted.indices) {
                            if (sourceSensor == GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE) {
                                if (i < result.inserted.size - 1) {
                                    if (abs(result.inserted[i].timestamp - result.inserted[i + 1].timestamp) < T.mins(1).msecs()) {
                                        repository.runTransactionForResult(InvalidateGlucoseValueTransaction(result.inserted[i].id))
                                            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating BG value", it) }
                                            .blockingGet()
                                            .also { result1 -> result1.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted and invalidated bg $it") } }
                                        repository.runTransactionForResult(InvalidateGlucoseValueTransaction(result.inserted[i + 1].id))
                                            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating BG value", it) }
                                            .blockingGet()
                                            .also { result1 -> result1.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted and invalidated bg $it") } }
                                        result.inserted.removeAt(i + 1)
                                        result.inserted.removeAt(i)
                                        continue
                                    }
                                }
                            }
                            aapsLogger.debug(LTag.DATABASE, "Inserted bg ${result.inserted[i]}")
                        }
                        result.updated.forEach {
                            aapsLogger.debug(LTag.DATABASE, "Updated bg $it")
                        }
                        result.sensorInsertionsInserted.forEach {
                            uel.log(
                                Action.CAREPORTAL,
                                Sources.Dexcom,
                                ValueWithUnit.Timestamp(it.timestamp),
                                ValueWithUnit.TherapyEventType(it.type)
                            )
                            aapsLogger.debug(LTag.DATABASE, "Inserted sensor insertion $it")
                        }
                        result.calibrationsInserted.forEach { calibration ->
                            calibration.glucose?.let { glucoseValue ->
                                uel.log(
                                    Action.CALIBRATION,
                                    Sources.Dexcom,
                                    ValueWithUnit.Timestamp(calibration.timestamp),
                                    ValueWithUnit.TherapyEventType(calibration.type),
                                    ValueWithUnit.fromGlucoseUnit(glucoseValue, calibration.glucoseUnit.toString)
                                )
                            }
                            aapsLogger.debug(LTag.DATABASE, "Inserted calibration $calibration")
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

    override fun findDexcomPackageName(): String? {
        val packageManager = context.packageManager
        for (packageInfo in packageManager.safeGetInstalledPackages(0)) {
            if (PACKAGE_NAMES.contains(packageInfo.packageName)) return packageInfo.packageName
        }
        return null
    }

    companion object {

        private val PACKAGE_NAMES = arrayOf(
            "com.dexcom.cgm.region1.mgdl", "com.dexcom.cgm.region1.mmol",
            "com.dexcom.cgm.region2.mgdl", "com.dexcom.cgm.region2.mmol",
            "com.dexcom.g6.region1.mmol", "com.dexcom.g6.region2.mgdl",
            "com.dexcom.g6.region3.mgdl", "com.dexcom.g6.region3.mmol",
            "com.dexcom.g6", "com.dexcom.g7"
        )
        const val PERMISSION = "com.dexcom.cgm.EXTERNAL_PERMISSION"
    }
}
