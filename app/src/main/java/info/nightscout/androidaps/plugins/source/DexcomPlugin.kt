package info.nightscout.androidaps.plugins.source

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.RequestDexcomPermissionActivity
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.extensions.fromConstant
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DexcomPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP,
    private val dexcomMediator: DexcomMediator,
    config: Config
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_dexcom_g6)
    .pluginName(R.string.dexcom_app_patched)
    .shortName(R.string.dexcom_short)
    .preferencesId(R.xml.pref_bgsourcedexcom)
    .description(R.string.description_source_dexcom),
    aapsLogger, resourceHelper, injector
), BgSource {

    init {
        if (!config.NSCLIENT) {
            pluginDescription.setDefault()
        }
    }

    override fun advancedFilteringSupported(): Boolean {
        return true
    }

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        (glucoseValue.sourceSensor == GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE ||
            glucoseValue.sourceSensor == GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE ||
            glucoseValue.sourceSensor == GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN)
            && sp.getBoolean(R.string.key_dexcomg5_nsupload, false)

    override fun onStart() {
        super.onStart()
        dexcomMediator.requestPermissionIfNeeded()
    }

    // cannot be inner class because of needed injection
    class DexcomWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var dexcomPlugin: DexcomPlugin
        @Inject lateinit var sp: SP
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var dataWorker: DataWorker
        @Inject lateinit var broadcastToXDrip: XDripBroadcast
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var uel: UserEntryLogger

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            var ret = Result.success()

            if (!dexcomPlugin.isEnabled(PluginType.BGSOURCE)) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val bundle = dataWorker.pickupBundle(inputData.getLong(DataWorker.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))
            try {
                val sourceSensor = when (bundle.getString("sensorType") ?: "") {
                    "G6" -> GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE
                    "G5" -> GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE
                    else -> GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN
                }
                val glucoseValuesBundle = bundle.getBundle("glucoseValues")
                    ?: return Result.failure(workDataOf("Error" to "missing glucoseValues"))
                val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
                for (i in 0 until glucoseValuesBundle.size()) {
                    val glucoseValueBundle = glucoseValuesBundle.getBundle(i.toString())!!
                    glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                        timestamp = glucoseValueBundle.getLong("timestamp") * 1000,
                        value = glucoseValueBundle.getInt("glucoseValue").toDouble(),
                        noise = null,
                        raw = null,
                        trendArrow = GlucoseValue.TrendArrow.fromString(glucoseValueBundle.getString("trendArrow")!!),
                        sourceSensor = sourceSensor
                    )
                }
                val calibrations = mutableListOf<CgmSourceTransaction.Calibration>()
                bundle.getBundle("meters")?.let { meters ->
                    for (i in 0 until meters.size()) {
                        meters.getBundle(i.toString())?.let {
                            val timestamp = it.getLong("timestamp") * 1000
                            val now = dateUtil.now()
                            val value = it.getInt("meterValue").toDouble()
                            if (timestamp > now - T.months(1).msecs() && timestamp < now) {
                                calibrations.add(CgmSourceTransaction.Calibration(
                                    timestamp = it.getLong("timestamp") * 1000,
                                    value = value,
                                    glucoseUnit = TherapyEvent.GlucoseUnit.fromConstant(Profile.unit(value))
                                ))
                            }
                        }
                    }
                }
                val sensorStartTime = if (sp.getBoolean(R.string.key_dexcom_lognssensorchange, false) && bundle.containsKey("sensorInsertionTime")) {
                    bundle.getLong("sensorInsertionTime", 0) * 1000
                } else {
                    null
                }
                repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, calibrations, sensorStartTime))
                    .doOnError {
                        aapsLogger.error(LTag.DATABASE, "Error while saving values from Dexcom App", it)
                        ret = Result.failure(workDataOf("Error" to it.toString()))
                    }
                    .blockingGet()
                    .also { result ->
                        result.inserted.forEach {
                            broadcastToXDrip(it)
                            aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                        }
                        result.updated.forEach {
                            broadcastToXDrip(it)
                            aapsLogger.debug(LTag.DATABASE, "Updated bg $it")
                        }
                        result.sensorInsertionsInserted.forEach {
                            uel.log(Action.CAREPORTAL,
                                Sources.Dexcom,
                                ValueWithUnit.Timestamp(it.timestamp),
                                ValueWithUnit.TherapyEventType(it.type))
                            aapsLogger.debug(LTag.DATABASE, "Inserted sensor insertion $it")
                        }
                        result.calibrationsInserted.forEach {
                            uel.log(Action.CAREPORTAL,
                                Sources.Dexcom,
                                ValueWithUnit.Timestamp(it.timestamp),
                                ValueWithUnit.TherapyEventType(it.type))
                            aapsLogger.debug(LTag.DATABASE, "Inserted calibration $it")
                        }
                    }
            } catch (e: Exception) {
                aapsLogger.error("Error while processing intent from Dexcom App", e)
                ret = Result.failure(workDataOf("Error" to e.toString()))
            }
            return ret
        }
    }

    companion object {

        private val PACKAGE_NAMES = arrayOf("com.dexcom.cgm.region1.mgdl", "com.dexcom.cgm.region1.mmol",
            "com.dexcom.cgm.region2.mgdl", "com.dexcom.cgm.region2.mmol",
            "com.dexcom.g6.region1.mmol", "com.dexcom.g6.region2.mgdl",
            "com.dexcom.g6.region3.mgdl", "com.dexcom.g6.region3.mmol")
        const val PERMISSION = "com.dexcom.cgm.EXTERNAL_PERMISSION"
    }

    class DexcomMediator @Inject constructor(val context: Context) {

        fun requestPermissionIfNeeded() {
            if (ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(context, RequestDexcomPermissionActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }

        fun findDexcomPackageName(): String? {
            val packageManager = context.packageManager
            for (packageInfo in packageManager.getInstalledPackages(0)) {
                if (PACKAGE_NAMES.contains(packageInfo.packageName)) return packageInfo.packageName
            }
            return null
        }
    }
}
