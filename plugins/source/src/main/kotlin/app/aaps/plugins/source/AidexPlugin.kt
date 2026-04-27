package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class AidexPlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    private val config: Config
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon((app.aaps.core.objects.R.drawable.ic_blooddrop_48))
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .pluginName(R.string.aidex)
        .shortName(R.string.aidex_short)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_aidex),
    ownPreferences = emptyList(),
    aapsLogger, rh,preferences
), BgSource {
    @Volatile
    private var _hasSensorError = false
    companion object {
        var sensorExpiredNotified = false
        var sensorErrorNotified = false
        var replaceSensorNotified = false
        var signalLostNotified = false
    }
    override fun hasSensorError(): Boolean = _hasSensorError

    // Allow only for pumpcontrol or dev & engineering_mode
    override fun specialEnableCondition(): Boolean {
        // return config.APS.not() || config.isDev() && config.isEngineeringMode()
        return true
    }

    // cannot be inner class because of needed injection
    class AidexWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var aidexPlugin: AidexPlugin
        @Inject lateinit var persistenceLayer: PersistenceLayer
        @Inject lateinit var dataWorkerStorage: DataWorkerStorage
        @Inject lateinit var preferences: Preferences
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var rxBus: RxBus
        @Inject lateinit var uiInteraction: UiInteraction

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!aidexPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val bundle = dataWorkerStorage.pickupBundle(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))

            aapsLogger.debug(LTag.BGSOURCE, "Received Aidex data: $bundle")

            if (bundle.containsKey(Intents.AIDEX_TRANSMITTER_SN)) aapsLogger.debug(LTag.BGSOURCE, "transmitterSerialNumber: " + bundle.getString(Intents.AIDEX_TRANSMITTER_SN))
            if (bundle.containsKey(Intents.AIDEX_SENSOR_ID)) aapsLogger.debug(LTag.BGSOURCE, "sensorId: " + bundle.getString(Intents.AIDEX_SENSOR_ID))

            val glucoseValues = mutableListOf<GV>()

            val timestamp = bundle.getLong(Intents.AIDEX_TIMESTAMP, 0)
            val bgType = bundle.getString(Intents.AIDEX_BG_TYPE, "mg/dl")
            val bgValue = bundle.getDouble(Intents.AIDEX_BG_VALUE, 0.0)

            val bgValueTarget = if (bgType.equals("mg/dl")) bgValue else bgValue * Constants.MMOLL_TO_MGDL

            val sensorExpired = bundle.getBoolean(Intents.AIDEX_SENSOR_EXPIRED, false)
            val sensorError = bundle.getBoolean(Intents.EXTRA_SENSOR_ERROR, false)
            val replaceSensor = bundle.getBoolean(Intents.EXTRA_REPLACE_SENSOR, false)
            val signalLost = bundle.getBoolean(Intents.EXTRA_SIGNAL_LOST, false)

            if (sensorExpired) {
                aapsLogger.warn(LTag.BGSOURCE, "Sensor expired detected!")
                if (!sensorExpiredNotified) {
                    sensorExpiredNotified = true
                    uiInteraction.addNotificationValidFor(
                        10001,
                        "Aidex传感器已过期，请更换新传感器",
                        Notification.URGENT,
                        60
                    )
                }
            } else {
                sensorExpiredNotified = false
            }
            if (replaceSensor) {
                aapsLogger.warn(LTag.BGSOURCE, "Sensor replacement required!")
                if (!replaceSensorNotified) {
                    replaceSensorNotified = true
                    uiInteraction.addNotificationValidFor(
                        10003,
                        "Aidex传感器需要更换，请尽快更换新传感器",
                        Notification.NORMAL,
                        120
                    )
                }
            } else {
                replaceSensorNotified = false
            }
            if (sensorError) {
                aapsLogger.error(LTag.BGSOURCE, "Sensor error detected!")
                if (!sensorErrorNotified) {
                    sensorErrorNotified = true
                    uiInteraction.addNotificationValidFor(
                        10002,
                        "Aidex传感器故障，请检查传感器状态",
                        Notification.URGENT,
                        60
                    )
                }
            } else {
                sensorErrorNotified = false
            }


            if (signalLost) {
                aapsLogger.warn(LTag.BGSOURCE, "Signal lost detected!")
                if (!signalLostNotified) {
                    signalLostNotified = true
                    uiInteraction.addNotificationValidFor(
                        10004,
                        "Aidex传感器信号丢失，请检查连接",
                        Notification.NORMAL,
                        30
                    )
                }
            } else {
                signalLostNotified = false
            }

            aidexPlugin._hasSensorError = sensorExpired || sensorError || replaceSensor || signalLost

            aapsLogger.debug(LTag.BGSOURCE, "Received Aidex broadcast [time=$timestamp, bgType=$bgType, value=$bgValue, targetValue=$bgValueTarget")

            glucoseValues += GV(
                timestamp = timestamp,
                value = bgValueTarget,
                raw = null,
                noise = null,
                trendArrow = TrendArrow.fromString(bundle.getString(Intents.AIDEX_BG_SLOPE_NAME)),
                sourceSensor = SourceSensor.AIDEX
            )
            persistenceLayer.insertCgmSourceData(Sources.Aidex, glucoseValues, emptyList(), null)
                .doOnError { ret = Result.failure(workDataOf("Error" to it.toString())) }
                .blockingGet()
            return ret
        }
    }
}
