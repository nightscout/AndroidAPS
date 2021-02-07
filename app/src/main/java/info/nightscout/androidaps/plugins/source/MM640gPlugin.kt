package info.nightscout.androidaps.plugins.source

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MM640gPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_generic_cgm)
    .pluginName(R.string.MM640g)
    .description(R.string.description_source_mm640g),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    // cannot be inner class because of needed injection
    class MM640gWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var mM640gPlugin: MM640gPlugin
        @Inject lateinit var aapsLogger: AAPSLogger

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            if (!mM640gPlugin.isEnabled(PluginType.BGSOURCE)) return Result.failure()
            val collection = inputData.getString("collection") ?: return Result.failure()
            if (collection == "entries") {
                val data = inputData.getString("data")
                aapsLogger.debug(LTag.BGSOURCE, "Received MM640g Data: $data")
                if (data != null && data.isNotEmpty()) {
                    try {
                        val jsonArray = JSONArray(data)
                        for (i in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.getJSONObject(i)
                            when (val type = jsonObject.getString("type")) {
                                "sgv" -> {
                                    val bgReading = BgReading()
                                    bgReading.value = jsonObject.getDouble("sgv")
                                    bgReading.direction = jsonObject.getString("direction")
                                    bgReading.date = jsonObject.getLong("date")
                                    bgReading.raw = jsonObject.getDouble("sgv")
                                    MainApp.getDbHelper().createIfNotExists(bgReading, "MM640g")
                                }

                                else  -> aapsLogger.debug(LTag.BGSOURCE, "Unknown entries type: $type")
                            }
                        }
                    } catch (e: JSONException) {
                        aapsLogger.error("Exception: ", e)
                    }
                }
            }
            return Result.success()
        }
    }
}