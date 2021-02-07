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
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TomatoPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_sensor)
    .pluginName(R.string.tomato)
    .preferencesId(R.xml.pref_bgsource)
    .shortName(R.string.tomato_short)
    .description(R.string.description_source_tomato),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    // cannot be inner class because of needed injection
    class TomatoWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var tomatoPlugin: TomatoPlugin
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var sp: SP
        @Inject lateinit var nsUpload: NSUpload

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            if (!tomatoPlugin.isEnabled(PluginType.BGSOURCE)) return Result.failure()
            val bgReading = BgReading()
            aapsLogger.debug(LTag.BGSOURCE, "Received Tomato Data")
            bgReading.value = inputData.getDouble("com.fanqies.tomatofn.Extras.BgEstimate", 0.0)
            bgReading.date = inputData.getLong("com.fanqies.tomatofn.Extras.Time", 0)
            val isNew = MainApp.getDbHelper().createIfNotExists(bgReading, "Tomato")
            if (isNew && sp.getBoolean(R.string.key_dexcomg5_nsupload, false)) {
                nsUpload.uploadBg(bgReading, "AndroidAPS-Tomato")
            }
            if (isNew && sp.getBoolean(R.string.key_dexcomg5_xdripupload, false)) {
                nsUpload.sendToXdrip(bgReading)
            }
            return Result.success()
        }
    }
}