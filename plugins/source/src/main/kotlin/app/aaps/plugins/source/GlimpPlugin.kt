package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlimpPlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences
) : AbstractBgSourcePlugin(
    pluginDescription = PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_glimp)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .pluginName(R.string.glimp)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_glimp),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences
), BgSource {

    // cannot be inner class because of needed injection
    class GlimpWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var glimpPlugin: GlimpPlugin
        @Inject lateinit var persistenceLayer: PersistenceLayer

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!glimpPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            aapsLogger.debug(LTag.BGSOURCE, "Received Glimp Data: $inputData}")
            val glucoseValues = mutableListOf<GV>()
            glucoseValues += GV(
                timestamp = inputData.getLong("myTimestamp", 0),
                value = inputData.getDouble("mySGV", 0.0),
                raw = null,
                noise = null,
                trendArrow = TrendArrow.fromString(inputData.getString("myTrend")),
                sourceSensor = SourceSensor.LIBRE_1_GLIMP
            )
            persistenceLayer.insertCgmSourceData(Sources.Glimp, glucoseValues, emptyList(), null)
                .doOnError { ret = Result.failure(workDataOf("Error" to it.toString())) }
                .blockingGet()
            return ret
        }
    }
}