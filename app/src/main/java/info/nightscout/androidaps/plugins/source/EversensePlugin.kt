package info.nightscout.androidaps.plugins.source

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.database.transactions.InsertIfNewByTimestampTherapyEventTransaction
import info.nightscout.androidaps.interfaces.BgSource
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EversensePlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_eversense)
    .pluginName(R.string.eversense)
    .shortName(R.string.eversense_shortname)
    .preferencesId(R.xml.pref_bgsource)
    .description(R.string.description_source_eversense),
    aapsLogger, rh, injector
), BgSource {

    override var sensorBatteryLevel = -1

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        glucoseValue.sourceSensor == GlucoseValue.SourceSensor.EVERSENSE && sp.getBoolean(R.string.key_dexcomg5_nsupload, false)

    // cannot be inner class because of needed injection
    class EversenseWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var eversensePlugin: EversensePlugin
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var dataWorker: DataWorker
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var xDripBroadcast: XDripBroadcast

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            var ret = Result.success()

            if (!eversensePlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val bundle = dataWorker.pickupBundle(inputData.getLong(DataWorker.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))
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
                            smoothed = null,
                            noise = null,
                            trendArrow = GlucoseValue.TrendArrow.NONE,
                            sourceSensor = GlucoseValue.SourceSensor.EVERSENSE
                        )
                    repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                        .doOnError {
                            aapsLogger.error(LTag.DATABASE, "Error while saving values from Eversense App", it)
                            ret = Result.failure(workDataOf("Error" to it.toString()))
                        }
                        .blockingGet()
                        .also { savedValues ->
                            savedValues.inserted.forEach {
                                xDripBroadcast.send(it)
                                aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                            }
                        }
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
                        repository.runTransactionForResult(InsertIfNewByTimestampTherapyEventTransaction(
                            timestamp = calibrationTimestamps[i],
                            type = TherapyEvent.Type.FINGER_STICK_BG_VALUE,
                            glucose = calibrationGlucoseLevels[i].toDouble(),
                            glucoseType = TherapyEvent.MeterType.FINGER,
                            glucoseUnit = TherapyEvent.GlucoseUnit.MGDL,
                            enteredBy = "AndroidAPS-Eversense"
                        ))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while saving therapy event", it)
                                ret = Result.failure(workDataOf("Error" to it.toString()))
                            }
                            .blockingGet()
                            .also { result ->
                                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted therapy event $it") }
                            }
                    }
                }
            }
            return ret
        }
    }
}