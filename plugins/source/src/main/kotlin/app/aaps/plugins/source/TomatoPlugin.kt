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
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.objects.workflow.MetroWorkerCreator
import app.aaps.core.ui.compose.icons.IcPluginTomato
import app.aaps.plugins.source.compose.BgSourceComposeContent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

// Registers itself into the plugin list. javax @Singleton stays: this module has Dagger interop on, so
// Metro reads it as the scope, and the class is still built by Metro only once.
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(470)
@SingleIn(AppScope::class)
class TomatoPlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { plugin ->
            BgSourceComposeContent(
                title = rh.gs(R.string.tomato)
            )
        }
        .icon(IcPluginTomato)
        .pluginName(TextRef.AndroidRes(R.string.tomato))
        .shortName(TextRef.AndroidRes(R.string.tomato_short))
        .preferencesVisibleInSimpleMode(false)
        .description(TextRef.AndroidRes(R.string.description_source_tomato)),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences, config
), BgSource {

    // cannot be inner class because of needed injection

    class TomatoWorker @AssistedInject constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        aapsLogger: AAPSLogger,
        fabricPrivacy: FabricPrivacy,
        private val tomatoPlugin: TomatoPlugin,
        private val persistenceLayer: PersistenceLayer
    ) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

        /**
         * Metro builds this worker. The parameter names must match [MetroWorkerCreator],
         * because Metro matches assisted parameters by name and not only by type.
         */
        @AssistedFactory
        fun interface Factory : MetroWorkerCreator {

            override fun create(context: Context, params: WorkerParameters): TomatoWorker
        }

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!tomatoPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val glucoseValues = mutableListOf<GV>()
            glucoseValues += GV(
                timestamp = inputData.getLong("com.fanqies.tomatofn.Extras.Time", 0),
                value = inputData.getDouble("com.fanqies.tomatofn.Extras.BgEstimate", 0.0),
                raw = null,
                noise = null,
                trendArrow = TrendArrow.NONE,
                sourceSensor = SourceSensor.LIBRE_1_TOMATO
            )
            try {
                persistenceLayer.insertCgmSourceData(Sources.Tomato, glucoseValues, emptyList(), null)
            } catch (e: Exception) {
                ret = Result.failure(workDataOf("Error" to e.toString()))
            }
            return ret
        }
    }
}