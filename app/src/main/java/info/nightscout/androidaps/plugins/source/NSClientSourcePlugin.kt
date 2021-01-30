package info.nightscout.androidaps.plugins.source

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
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

    // cannot be inner class because of needed injection
    class NSClientSourceWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var nsClientSourcePlugin: NSClientSourcePlugin
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var sp: SP

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            if (!nsClientSourcePlugin.isEnabled(PluginType.BGSOURCE) && !sp.getBoolean(R.string.key_ns_autobackfill, true)) return Result.failure()
            try {
                inputData.getString("sgv")?.let { sgvString ->
                    aapsLogger.debug(LTag.BGSOURCE, "Received NS Data: $sgvString")
                    val sgvJson = JSONObject(sgvString)
                    nsClientSourcePlugin.storeSgv(sgvJson)
                }
                inputData.getString("sgvs")?.let { sgvString ->
                    aapsLogger.debug(LTag.BGSOURCE, "Received NS Data: $sgvString")
                    val jsonArray = JSONArray(sgvString)
                    for (i in 0 until jsonArray.length()) {
                        val sgvJson = jsonArray.getJSONObject(i)
                        nsClientSourcePlugin.storeSgv(sgvJson)
                    }
                }
            } catch (e: Exception) {
                aapsLogger.error("Unhandled exception", e)
                return Result.failure()
            }
            // Objectives 0
            sp.putBoolean(R.string.key_ObjectivesbgIsAvailableInNS, true)
            return Result.success()
        }
    }
}