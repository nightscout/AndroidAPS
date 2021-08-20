package info.nightscout.androidaps.plugins.source

import android.content.Intent
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSgv
import info.nightscout.androidaps.utils.JsonHelper.safeGetLong
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NSClientSourcePlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP,
    config: Config
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_nsclient_bg)
    .pluginName(R.string.nsclientbg)
    .description(R.string.description_source_ns_client),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    private var lastBGTimeStamp: Long = 0
    private var isAdvancedFilteringEnabled = false

    init {
        if (config.NSCLIENT) {
            pluginDescription
                .alwaysEnabled(true)
                .setDefault()
        }
    }

    override fun advancedFilteringSupported(): Boolean {
        return isAdvancedFilteringEnabled
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE) && !sp.getBoolean(R.string.key_ns_autobackfill, true)) return
        val bundles = intent.extras ?: return
        try {
            if (bundles.containsKey("sgv")) {
                val sgvString = bundles.getString("sgv")
                aapsLogger.debug(LTag.BGSOURCE, "Received NS Data: $sgvString")
                val sgvJson = JSONObject(sgvString)
                storeSgv(sgvJson)
            }
            if (bundles.containsKey("sgvs")) {
                val sgvString = bundles.getString("sgvs")
                aapsLogger.debug(LTag.BGSOURCE, "Received NS Data: $sgvString")
                val jsonArray = JSONArray(sgvString)
                for (i in 0 until jsonArray.length()) {
                    val sgvJson = jsonArray.getJSONObject(i)
                    storeSgv(sgvJson)
                }
            }
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
        // Objectives 0
        sp.putBoolean(R.string.key_ObjectivesbgIsAvailableInNS, true)
    }

    private fun storeSgv(sgvJson: JSONObject) {
        val nsSgv = NSSgv(sgvJson)
        val bgReading = BgReading(injector, nsSgv)
        MainApp.getDbHelper().createIfNotExists(bgReading, "NS")
        detectSource(safeGetString(sgvJson, "device", "none"), safeGetLong(sgvJson, "mills"))
    }

    private fun detectSource(source: String, timeStamp: Long) {
        if (timeStamp > lastBGTimeStamp) {
            isAdvancedFilteringEnabled = source.contains("G5 Native") || source.contains("G6 Native") || source.contains("AndroidAPS-DexcomG5") || source.contains("AndroidAPS-DexcomG6")
            lastBGTimeStamp = timeStamp
        }
    }
}