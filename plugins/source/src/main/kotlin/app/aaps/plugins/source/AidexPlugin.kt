package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
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
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.ui.compose.icons.IcGenericCgm
import app.aaps.plugins.source.compose.BgSourceComposeContent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AidexPlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
    private val notificationManager: NotificationManager,
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { plugin ->
            BgSourceComposeContent(
                title = rh.gs(R.string.aidex)
            )
        }
        .icon(IcGenericCgm)
        .pluginName(R.string.aidex)
        .shortName(R.string.aidex_short)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_aidex),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences, config
), BgSource {

    @Volatile
    private var _hasSensorError = false

    // Per-condition "already-notified" latches: prevent re-posting the same notification on every
    // BG event while the underlying sensor flag stays true. Reset when the flag clears.
    private var sensorExpiredNotified = false
    private var sensorErrorNotified = false
    private var sensorStablingNotified = false
    private var replaceSensorNotified = false
    private var signalLostNotified = false

    override fun hasSensorError(): Boolean = _hasSensorError

    override fun specialEnableCondition(): Boolean {
        return true
    }

    @HiltWorker
    class AidexWorker @AssistedInject constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        aapsLogger: AAPSLogger,
        fabricPrivacy: FabricPrivacy,
        private val aidexPlugin: AidexPlugin,
        private val persistenceLayer: PersistenceLayer,
        private val rxBus: RxBus
    ) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!aidexPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val timestamp = inputData.getLong(Intents.AIDEX_TIMESTAMP, 0)
            val bgType = inputData.getString(Intents.AIDEX_BG_TYPE) ?: "mg/dl"
            val bgValue = inputData.getDouble(Intents.AIDEX_BG_VALUE, 0.0)
            val bgSlopeName = inputData.getString(Intents.AIDEX_BG_SLOPE_NAME)
            val transmitterSN = inputData.getString(Intents.AIDEX_TRANSMITTER_SN)
            val sensorId = inputData.getString(Intents.AIDEX_SENSOR_ID)

            aapsLogger.debug(LTag.BGSOURCE, "Received Aidex data [timestamp=$timestamp, bgType=$bgType, bgValue=$bgValue]")
            if (transmitterSN != null) aapsLogger.debug(LTag.BGSOURCE, "transmitterSerialNumber: $transmitterSN")
            if (sensorId != null) aapsLogger.debug(LTag.BGSOURCE, "sensorId: $sensorId")

            val bgValueTarget = if (bgType == "mg/dl") bgValue else bgValue * Constants.MMOLL_TO_MGDL

            val sensorExpired = inputData.getBoolean(Intents.AIDEX_SENSOR_EXPIRED, false)
            val sensorError = inputData.getBoolean(Intents.EXTRA_SENSOR_ERROR, false)
            val sensorStabling = inputData.getBoolean(Intents.EXTRA_SENSOR_STABILIZING, false)
            val replaceSensor = inputData.getBoolean(Intents.EXTRA_REPLACE_SENSOR, false)
            val signalLost = inputData.getBoolean(Intents.EXTRA_SIGNAL_LOST, false)

            aidexPlugin.handleSensorNotifications(sensorExpired, sensorError, sensorStabling, replaceSensor, signalLost)

            aidexPlugin._hasSensorError = sensorExpired || sensorError || replaceSensor || signalLost || sensorStabling

            aapsLogger.debug(LTag.BGSOURCE, "Received Aidex broadcast [time=$timestamp, bgType=$bgType, value=$bgValue, targetValue=$bgValueTarget]")

            val isValidValue = bgValueTarget > 0 && !aidexPlugin._hasSensorError

            if (isValidValue) {
                val glucoseValues = mutableListOf<GV>()
                glucoseValues += GV(
                    timestamp = timestamp,
                    value = bgValueTarget,
                    raw = null,
                    noise = null,
                    trendArrow = TrendArrow.fromString(bgSlopeName),
                    sourceSensor = SourceSensor.AIDEX
                )
                try {
                    persistenceLayer.insertCgmSourceData(Sources.Aidex, glucoseValues, emptyList(), null)
                    rxBus.send(EventRefreshOverview("AidexPlugin"))
                } catch (e: Exception) {
                    ret = Result.failure(workDataOf("Error" to e.toString()))
                }
            } else {
                aapsLogger.warn(LTag.BGSOURCE, "Invalid glucose value ignored: value=$bgValue, hasError=${aidexPlugin._hasSensorError}")
            }

            return ret
        }
    }

    @VisibleForTesting
    internal fun handleSensorNotifications(
        sensorExpired: Boolean,
        sensorError: Boolean,
        sensorStabling: Boolean,
        replaceSensor: Boolean,
        signalLost: Boolean
    ) {
        if (sensorExpired) {
            aapsLogger.warn(LTag.BGSOURCE, "Sensor expired detected!")
            if (!sensorExpiredNotified) {
                sensorExpiredNotified = true
                notificationManager.post(
                    id = NotificationId.AIDEX_SENSOR_EXPIRED,
                    textRes = R.string.aidex_sensor_expired,
                    level = NotificationLevel.IMPORTANT,
                    validMinutes = 60
                )
            }
        } else {
            sensorExpiredNotified = false
            notificationManager.dismiss(NotificationId.AIDEX_SENSOR_EXPIRED)
        }

        if (replaceSensor) {
            aapsLogger.warn(LTag.BGSOURCE, "Sensor replacement required!")
            if (!replaceSensorNotified) {
                replaceSensorNotified = true
                notificationManager.post(
                    id = NotificationId.AIDEX_REPLACE_SENSOR,
                    textRes = R.string.aidex_sensor_replace,
                    level = NotificationLevel.NORMAL,
                    validMinutes = 120
                )
            }
        } else {
            replaceSensorNotified = false
            notificationManager.dismiss(NotificationId.AIDEX_REPLACE_SENSOR)
        }

        if (sensorError) {
            aapsLogger.error(LTag.BGSOURCE, "Sensor error detected!")
            if (!sensorErrorNotified) {
                sensorErrorNotified = true
                notificationManager.post(
                    id = NotificationId.AIDEX_SENSOR_ERROR,
                    textRes = R.string.aidex_sensor_error,
                    level = NotificationLevel.IMPORTANT,
                    validMinutes = 60
                )
            }
        } else {
            sensorErrorNotified = false
            notificationManager.dismiss(NotificationId.AIDEX_SENSOR_ERROR)
        }

        if (sensorStabling) {
            aapsLogger.error(LTag.BGSOURCE, "Sensor is not stable detected!")
            if (!sensorStablingNotified) {
                sensorStablingNotified = true
                notificationManager.post(
                    id = NotificationId.AIDEX_SENSOR_STABILIZING,
                    textRes = R.string.aidex_sensor_stabilizing,
                    level = NotificationLevel.NORMAL,
                    validMinutes = 60
                )
            }
        } else {
            sensorStablingNotified = false
            notificationManager.dismiss(NotificationId.AIDEX_SENSOR_STABILIZING)
        }

        if (signalLost) {
            aapsLogger.warn(LTag.BGSOURCE, "Signal lost detected!")
            if (!signalLostNotified) {
                signalLostNotified = true
                notificationManager.post(
                    id = NotificationId.AIDEX_SIGNAL_LOST,
                    textRes = R.string.aidex_signal_lost,
                    level = NotificationLevel.NORMAL,
                    validMinutes = 30
                )
            }
        } else {
            signalLostNotified = false
            notificationManager.dismiss(NotificationId.AIDEX_SIGNAL_LOST)
        }
    }
}
