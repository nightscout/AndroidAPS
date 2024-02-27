package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
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
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.Dispatchers
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EversensePlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_eversense)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .pluginName(R.string.eversense)
        .shortName(R.string.eversense_shortname)
        .description(R.string.description_source_eversense),
    aapsLogger, rh
), BgSource {

    override var sensorBatteryLevel = -1

    // cannot be inner class because of needed injection
    class EversenseWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var eversensePlugin: EversensePlugin
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var dataWorkerStorage: DataWorkerStorage
        @Inject lateinit var persistenceLayer: PersistenceLayer

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!eversensePlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val bundle = dataWorkerStorage.pickupBundle(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
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
            val glucoseValues = mutableListOf<GV>()
            if (bundle.containsKey("glucoseLevels")) {
                val glucoseLevels = bundle.getIntArray("glucoseLevels")
                val glucoseRecordNumbers = bundle.getIntArray("glucoseRecordNumbers")
                val glucoseTimestamps = bundle.getLongArray("glucoseTimestamps")
                if (glucoseLevels != null && glucoseRecordNumbers != null && glucoseTimestamps != null) {
                    aapsLogger.debug(LTag.BGSOURCE, "glucoseLevels" + Arrays.toString(glucoseLevels))
                    aapsLogger.debug(LTag.BGSOURCE, "glucoseRecordNumbers" + Arrays.toString(glucoseRecordNumbers))
                    aapsLogger.debug(LTag.BGSOURCE, "glucoseTimestamps" + Arrays.toString(glucoseTimestamps))
                    for (i in glucoseLevels.indices)
                        glucoseValues += GV(
                            timestamp = glucoseTimestamps[i],
                            value = glucoseLevels[i].toDouble(),
                            raw = glucoseLevels[i].toDouble(),
                            noise = null,
                            trendArrow = TrendArrow.NONE,
                            sourceSensor = SourceSensor.EVERSENSE
                        )
                }
            }
            val calibrations = mutableListOf<PersistenceLayer.Calibration>()
            if (bundle.containsKey("calibrationGlucoseLevels")) {
                val calibrationGlucoseLevels = bundle.getIntArray("calibrationGlucoseLevels")
                val calibrationTimestamps = bundle.getLongArray("calibrationTimestamps")
                val calibrationRecordNumbers = bundle.getLongArray("calibrationRecordNumbers")
                if (calibrationGlucoseLevels != null && calibrationTimestamps != null && calibrationRecordNumbers != null) {
                    aapsLogger.debug(LTag.BGSOURCE, "calibrationGlucoseLevels" + Arrays.toString(calibrationGlucoseLevels))
                    aapsLogger.debug(LTag.BGSOURCE, "calibrationTimestamps" + Arrays.toString(calibrationTimestamps))
                    aapsLogger.debug(LTag.BGSOURCE, "calibrationRecordNumbers" + Arrays.toString(calibrationRecordNumbers))
                    for (i in calibrationGlucoseLevels.indices) {
                        calibrations.add(
                            PersistenceLayer.Calibration(
                                timestamp = calibrationTimestamps[i],
                                value = calibrationGlucoseLevels[i].toDouble(),
                                glucoseUnit = GlucoseUnit.MGDL
                            )
                        )
                    }
                }
                persistenceLayer.insertCgmSourceData(Sources.Eversense, glucoseValues, emptyList(), null)
                    .doOnError { ret = Result.failure(workDataOf("Error" to it.toString())) }
                    .blockingGet()
            }
            return ret
        }
    }
}