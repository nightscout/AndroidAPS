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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlimpPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_glimp)
    .pluginName(R.string.Glimp)
    .preferencesId(R.xml.pref_bgsource)
    .description(R.string.description_source_glimp),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    // cannot be inner class because of needed injection
    class GlimpWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var glimpPlugin: GlimpPlugin
        @Inject lateinit var aapsLogger: AAPSLogger

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            if (!glimpPlugin.isEnabled(PluginType.BGSOURCE)) return Result.failure()
            aapsLogger.debug(LTag.BGSOURCE, "Received Glimp Data: $inputData}")
            val bgReading = BgReading()
            bgReading.value = inputData.getDouble("mySGV", 0.0)
            bgReading.direction = inputData.getString("myTrend")
            bgReading.date = inputData.getLong("myTimestamp", 0)
            bgReading.raw = 0.0
            MainApp.getDbHelper().createIfNotExists(bgReading, "GLIMP")
            return Result.success()
        }
    }
}