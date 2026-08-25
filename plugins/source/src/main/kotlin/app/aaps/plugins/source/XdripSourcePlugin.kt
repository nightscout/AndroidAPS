package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.ui.compose.icons.IcXDrip
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.core.utils.receivers.Inbox
import app.aaps.plugins.source.compose.BgSourceComposeContent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.round

@Singleton
class XdripSourcePlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
) : AbstractBgSourceWithSensorInsertLogPlugin(
    pluginDescription = PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { plugin ->
            BgSourceComposeContent(
                title = rh.gs(R.string.source_xdrip)
            )
        }
        .icon(IcXDrip)
        .pluginName(R.string.source_xdrip)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_xdrip),
    aapsLogger = aapsLogger,
    rh = rh,
    preferences = preferences
), BgSource, XDripSource {

    override var sensorBatteryLevel = -1

    // cannot be inner class because of needed injection
    @HiltWorker
    class XdripSourceWorker @AssistedInject constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        aapsLogger: AAPSLogger,
        fabricPrivacy: FabricPrivacy,
        private val xdripSourcePlugin: XdripSourcePlugin,
        private val persistenceLayer: PersistenceLayer,
        private val preferences: Preferences,
        private val dateUtil: DateUtil,
        private val dataInbox: DataInbox
    ) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

        fun getSensorStartTime(bundle: Bundle): Long? {
            val now = dateUtil.now()
            var sensorStartTime: Long? = if (preferences.get(BooleanKey.BgSourceCreateSensorChange)) {
                bundle.getLong(Intents.EXTRA_SENSOR_STARTED_AT, 0)
            } else {
                null
            }
            // check start time validity
            sensorStartTime?.let {
                if (abs(it - now) > T.months(1).msecs() || it > now) sensorStartTime = null
            }
            return sensorStartTime
        }

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            // Drain first, unconditionally: drain() clears DataInbox's pending-work gate, so every
            // enqueued worker MUST reach it. If we returned early (plugin disabled) before draining,
            // the gate would stay set and silently block all future enqueues for this slot until
            // the process restarts. Bundles drained while disabled are intentionally discarded.
            val bundles = dataInbox.drain(XdripInbox)
            if (!xdripSourcePlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            if (bundles.isEmpty()) return Result.success(workDataOf("Result" to "no data"))

            var hadFailure = false
            for ((index, bundle) in bundles.withIndex()) {
                try {
                    processBundle(bundle)
                } catch (e: CancellationException) {
                    // WorkManager stopped this run (e.g. chain overload / run-attempt limit). The
                    // coroutine contract requires CancellationException to propagate, otherwise the
                    // loop keeps fighting a cancelled Job and spams failures for every remaining
                    // bundle. drain() already removed the whole batch, so re-queue this bundle plus
                    // everything not yet processed before propagating — otherwise those readings are
                    // lost. requeue/enqueue are non-suspending, so they complete despite cancellation.
                    dataInbox.requeue(XdripInbox, bundles.subList(index, bundles.size))
                    throw e
                } catch (e: Exception) {
                    // processBundle early-returns on malformed-bundle conditions; anything that
                    // reaches the catch is a real exception (typically a DB write). Surface as
                    // failure so WorkInfo reflects the truth.
                    aapsLogger.error(LTag.BGSOURCE, "Failed processing xDrip bundle", e)
                    hadFailure = true
                }
            }
            return if (hadFailure) Result.failure(workDataOf("Error" to "one or more bundles failed")) else Result.success()
        }

        private suspend fun processBundle(bundle: Bundle) {
            aapsLogger.debug(LTag.BGSOURCE, "Received xDrip data: $bundle")
            val glucoseValues = mutableListOf<GV>()
            glucoseValues += GV(
                timestamp = bundle.getLong(Intents.EXTRA_TIMESTAMP, 0),
                value = round(bundle.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0)),
                raw = round(bundle.getDouble(Intents.EXTRA_RAW, 0.0)),
                noise = null,
                trendArrow = TrendArrow.fromString(bundle.getString(Intents.EXTRA_BG_SLOPE_NAME)),
                sourceSensor = SourceSensor.fromString(bundle.getString(Intents.XDRIP_DATA_SOURCE) ?: "")
            )
            val newSensorStartTime = getSensorStartTime(bundle)
            // Retrieve last stored sensorStartTime from the database
            val lastTherapyEvent = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)
            val lastStoredSensorStartTime = lastTherapyEvent?.timestamp
            // Decide whether to update sensorStartTime or keep the last stored one
            val finalSensorStartTime = when {
                lastStoredSensorStartTime != null && newSensorStartTime != null &&
                    abs(newSensorStartTime - lastStoredSensorStartTime) <= 300_000 -> {
                    aapsLogger.debug(LTag.BGSOURCE, "Sensor start time is within 5 minutes range, skipping update.")
                    null
                }

                lastStoredSensorStartTime != null && newSensorStartTime != null &&
                    newSensorStartTime < lastStoredSensorStartTime                 -> {
                    aapsLogger.debug(LTag.BGSOURCE, "Sensor start time is older than last stored time, skipping update.")
                    null
                }

                else                                                               -> newSensorStartTime
            }
            // Always update glucoseValues, but use the decided sensorStartTime
            if (glucoseValues[0].timestamp > 0 && glucoseValues[0].value > 0.0) {
                persistenceLayer.insertCgmSourceData(Sources.Xdrip, glucoseValues, emptyList(), finalSensorStartTime)
            } else {
                aapsLogger.warn(LTag.BGSOURCE, "Skipping xDrip bundle: missing glucoseValue")
                return
            }
            xdripSourcePlugin.sensorBatteryLevel = bundle.getInt(Intents.EXTRA_SENSOR_BATTERY, -1)
        }
    }
}

object XdripInbox : Inbox<Bundle>("xdrip-bg", XdripSourcePlugin.XdripSourceWorker::class.java)
