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
import app.aaps.core.objects.workflow.LoggingWorker
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OttaiPlugin @Inject constructor(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_ottai)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .pluginName(R.string.ottai_app)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_source_patched_ottai_app),
    aapsLogger, rh
), BgSource {
    class OttaiWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var ottaiPlugin: OttaiPlugin
        @Inject lateinit var persistenceLayer: PersistenceLayer

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()
            if (!ottaiPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val collection = inputData.getString("collection") ?: return Result.failure(workDataOf("Error" to "missing collection"))
            if (collection == "entries"){
                val data = inputData.getString("data")
                aapsLogger.debug(LTag.BGSOURCE, "Received Ottai Data $data")
                if (!data.isNullOrEmpty()){
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
                                        raw = jsonObject.getDouble("sgv"),
                                        noise = null,
                                        trendArrow = TrendArrow.fromString(jsonObject.getString("direction")),
                                        sourceSensor = SourceSensor.OTTAI
                                    )
                                else  -> aapsLogger.debug(LTag.BGSOURCE, "Unknown entries type: $type")
                            }
                        }
                        persistenceLayer.insertCgmSourceData(Sources.Ottai, glucoseValues, emptyList(), null)
                            .doOnError { ret = Result.failure(workDataOf("Error" to it.toString())) }
                            .blockingGet()
                    } catch (e: JSONException) {
                        aapsLogger.error("Exception: ", e)
                        ret = Result.failure(workDataOf("Error" to e.toString()))
                    }
                }
            }
            return ret
        }
    }
}