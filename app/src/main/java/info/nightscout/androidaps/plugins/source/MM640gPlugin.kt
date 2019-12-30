package info.nightscout.androidaps.plugins.source

import android.content.Intent
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MM640gPlugin @Inject constructor(
    private val aapsLogger: AAPSLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.MM640g)
    .description(R.string.description_source_mm640g)
), BgSourceInterface {

    override fun advancedFilteringSupported(): Boolean {
        return false
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        val bundle = intent.extras ?: return
        val collection = bundle.getString("collection") ?: return
        if (collection == "entries") {
            val data = bundle.getString("data")
            aapsLogger.debug(LTag.BGSOURCE, "Received MM640g Data: $data")
            if (data != null && data.isNotEmpty()) {
                try {
                    val jsonArray = JSONArray(data)
                    for (i in 0 until jsonArray.length()) {
                        val json_object = jsonArray.getJSONObject(i)
                        val type = json_object.getString("type")
                        when (type) {
                            "sgv" -> {
                                val bgReading = BgReading()
                                bgReading.value = json_object.getDouble("sgv")
                                bgReading.direction = json_object.getString("direction")
                                bgReading.date = json_object.getLong("date")
                                bgReading.raw = json_object.getDouble("sgv")
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
    }
}