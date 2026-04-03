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
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.ui.compose.icons.IcGenericCgm
import app.aaps.core.ui.compose.icons.IcPluginMM640G
import app.aaps.plugins.source.compose.BgSourceComposeContent
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MM640gPlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
) : AbstractBgSourcePlugin(
    pluginDescription = PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { plugin ->
            BgSourceComposeContent(
                title = rh.gs(R.string.mm640g)
            )
        }
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_generic_cgm)
        .icon(IcPluginMM640G)
        .pluginName(R.string.mm640g)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_mm640g),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences, config
), BgSource {

    // cannot be inner class because of needed injection
    class MM640gWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var mM640gPlugin: MM640gPlugin
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var persistenceLayer: PersistenceLayer

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!mM640gPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val collection = inputData.getString("collection") ?: return Result.failure(workDataOf("Error" to "missing collection"))
            if (collection == "entries") {
                val data = inputData.getString("data")
                aapsLogger.debug(LTag.BGSOURCE, "Received MM640g Data: $data")
                if (!data.isNullOrEmpty()) {
                    try {
                        val glucoseValues = mutableListOf<GV>()
                        val jsonArray = JSONArray(data)
                        for (i in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.getJSONObject(i)
                            when (val type = jsonObject.getString("type")) {
                                "sgv" ->
                                    glucoseValues += GV(
                                        timestamp = jsonObject.getLong("date"),
                                        value = jsonObject.getDouble("sgv"),
                                        raw = null,
                                        noise = null,
                                        trendArrow = TrendArrow.fromString(jsonObject.getString("direction")),
                                        sourceSensor = SourceSensor.MM_600_SERIES
                                    )

                                else  -> aapsLogger.debug(LTag.BGSOURCE, "Unknown entries type: $type")
                            }
                        }
                        try {
                            persistenceLayer.insertCgmSourceData(Sources.MM640g, glucoseValues, emptyList(), null)
                        } catch (e: Exception) {
                            ret = Result.failure(workDataOf("Error" to e.toString()))
                        }
                    } catch (e: JSONException) {
                        aapsLogger.error("Exception: ", e)
                        ret = Result.failure(workDataOf("Error" to e.toString()))
                    }
                }
            } else {
                ret = Result.failure(workDataOf("Error" to "missing input data"))
            }
            return ret
        }
    }
}