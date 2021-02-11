package info.nightscout.androidaps.plugins.source

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.receivers.BundleStore
import info.nightscout.androidaps.receivers.DataReceiver
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EversensePlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_eversense)
    .pluginName(R.string.eversense)
    .shortName(R.string.eversense_shortname)
    .preferencesId(R.xml.pref_bgsource)
    .description(R.string.description_source_eversense),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    override var sensorBatteryLevel = -1

    private val disposable = CompositeDisposable()

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    // cannot be inner class because of needed injection
    class EversenseWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var eversensePlugin: EversensePlugin
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var sp: SP
        @Inject lateinit var nsUpload: NSUpload
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var bundleStore: BundleStore
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var broadcastToXDrip: XDripBroadcast

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            if (!eversensePlugin.isEnabled(PluginType.BGSOURCE)) return Result.failure()
            val bundle = bundleStore.pickup(inputData.getLong(DataReceiver.STORE_KEY, -1))
                ?: return Result.failure()
            if (bundle.containsKey("currentCalibrationPhase")) aapsLogger.debug(LTag.BGSOURCE, "currentCalibrationPhase: " + bundle.getString("currentCalibrationPhase"))
            if (bundle.containsKey("placementModeInProgress")) aapsLogger.debug(LTag.BGSOURCE, "placementModeInProgress: " + bundle.getBoolean("placementModeInProgress"))
            if (bundle.containsKey("glucoseLevel")) aapsLogger.debug(LTag.BGSOURCE, "glucoseLevel: " + bundle.getInt("glucoseLevel"))
            if (bundle.containsKey("glucoseTrendDirection")) aapsLogger.debug(LTag.BGSOURCE, "glucoseTrendDirection: " + bundle.getString("glucoseTrendDirection"))
            if (bundle.containsKey("glucoseTimestamp")) aapsLogger.debug(LTag.BGSOURCE, "glucoseTimestamp: " + dateUtil.dateAndTimeString(bundle.getLong("glucoseTimestamp")))
            if (bundle.containsKey("batteryLevel")) {
                aapsLogger.debug(LTag.BGSOURCE, "batteryLevel: " + bundle.getString("batteryLevel"))
                //sensorBatteryLevel = bundle.getString("batteryLevel").toInt()
                // TODO: Line to check I don't have eversense so I don't know what kind of information is sent...
            }
            if (bundle.containsKey("signalStrength")) aapsLogger.debug(LTag.BGSOURCE, "signalStrength: " + bundle.getString("signalStrength"))
            if (bundle.containsKey("transmitterVersionNumber")) aapsLogger.debug(LTag.BGSOURCE, "transmitterVersionNumber: " + bundle.getString("transmitterVersionNumber"))
            if (bundle.containsKey("isXLVersion")) aapsLogger.debug(LTag.BGSOURCE, "isXLVersion: " + bundle.getBoolean("isXLVersion"))
            if (bundle.containsKey("transmitterModelNumber")) aapsLogger.debug(LTag.BGSOURCE, "transmitterModelNumber: " + bundle.getString("transmitterModelNumber"))
            if (bundle.containsKey("transmitterSerialNumber")) aapsLogger.debug(LTag.BGSOURCE, "transmitterSerialNumber: " + bundle.getString("transmitterSerialNumber"))
            if (bundle.containsKey("transmitterAddress")) aapsLogger.debug(LTag.BGSOURCE, "transmitterAddress: " + bundle.getString("transmitterAddress"))
            if (bundle.containsKey("sensorInsertionTimestamp")) aapsLogger.debug(LTag.BGSOURCE, "sensorInsertionTimestamp: " + dateUtil.dateAndTimeString(bundle.getLong("sensorInsertionTimestamp")))
            if (bundle.containsKey("transmitterVersionNumber")) aapsLogger.debug(LTag.BGSOURCE, "transmitterVersionNumber: " + bundle.getString("transmitterVersionNumber"))
            if (bundle.containsKey("transmitterConnectionState")) aapsLogger.debug(LTag.BGSOURCE, "transmitterConnectionState: " + bundle.getString("transmitterConnectionState"))
            if (bundle.containsKey("glucoseLevels")) {
                val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
                val glucoseLevels = bundle.getIntArray("glucoseLevels")
                val glucoseRecordNumbers = bundle.getIntArray("glucoseRecordNumbers")
                val glucoseTimestamps = bundle.getLongArray("glucoseTimestamps")
                if (glucoseLevels != null && glucoseRecordNumbers != null && glucoseTimestamps != null) {
                    aapsLogger.debug(LTag.BGSOURCE, "glucoseLevels" + Arrays.toString(glucoseLevels))
                    aapsLogger.debug(LTag.BGSOURCE, "glucoseRecordNumbers" + Arrays.toString(glucoseRecordNumbers))
                    aapsLogger.debug(LTag.BGSOURCE, "glucoseTimestamps" + Arrays.toString(glucoseTimestamps))
                    for (i in glucoseLevels.indices)
                        glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                            timestamp = glucoseTimestamps[i],
                            value = glucoseLevels[i].toDouble(),
                            raw = glucoseLevels[i].toDouble(),
                            noise = null,
                            trendArrow = GlucoseValue.TrendArrow.NONE,
                            sourceSensor = GlucoseValue.SourceSensor.EVERSENSE
                        )
                    eversensePlugin.disposable += repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null)).subscribe({ savedValues ->
                        savedValues.inserted.forEach {
                            broadcastToXDrip(it)
                            if (sp.getBoolean(R.string.key_dexcomg5_nsupload, false))
                                nsUpload.uploadBg(it, GlucoseValue.SourceSensor.EVERSENSE.text)
                        }
                    }, {
                        aapsLogger.error(LTag.BGSOURCE, "Error while saving values from Eversense App", it)
                    })
                }
            }
            if (bundle.containsKey("calibrationGlucoseLevels")) {
                val calibrationGlucoseLevels = bundle.getIntArray("calibrationGlucoseLevels")
                val calibrationTimestamps = bundle.getLongArray("calibrationTimestamps")
                val calibrationRecordNumbers = bundle.getLongArray("calibrationRecordNumbers")
                if (calibrationGlucoseLevels != null && calibrationTimestamps != null && calibrationRecordNumbers != null) {
                    aapsLogger.debug(LTag.BGSOURCE, "calibrationGlucoseLevels" + Arrays.toString(calibrationGlucoseLevels))
                    aapsLogger.debug(LTag.BGSOURCE, "calibrationTimestamps" + Arrays.toString(calibrationTimestamps))
                    aapsLogger.debug(LTag.BGSOURCE, "calibrationRecordNumbers" + Arrays.toString(calibrationRecordNumbers))
                    for (i in calibrationGlucoseLevels.indices) {
                        try {
                            if (MainApp.getDbHelper().getCareportalEventFromTimestamp(calibrationTimestamps[i]) == null) {
                                val data = JSONObject()
                                data.put("enteredBy", "AndroidAPS-Eversense")
                                data.put("created_at", DateUtil.toISOString(calibrationTimestamps[i]))
                                data.put("eventType", CareportalEvent.BGCHECK)
                                data.put("glucoseType", "Finger")
                                data.put("glucose", calibrationGlucoseLevels[i])
                                data.put("units", Constants.MGDL)
                                nsUpload.uploadCareportalEntryToNS(data)
                            }
                        } catch (e: JSONException) {
                            aapsLogger.error("Unhandled exception", e)
                        }
                    }
                }
            }
            return Result.success()
        }
    }
}