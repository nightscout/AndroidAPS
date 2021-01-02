package info.nightscout.androidaps.plugins.source

import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
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
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.isRunningTest
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

@Singleton
class RandomBgPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val virtualPumpPlugin: VirtualPumpPlugin,
    private val buildHelper: BuildHelper,
    private val sp: SP,
    private val nsUpload: NSUpload
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_dice)
    .pluginName(R.string.randombg)
    .shortName(R.string.randombg_short)
    .preferencesId(R.xml.pref_bgsource)
    .description(R.string.description_source_randombg),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    private val loopHandler : Handler = Handler(HandlerThread(RandomBgPlugin::class.java.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    companion object {
        const val interval = 5L // minutes
    }

    init {
        refreshLoop = Runnable {
            handleNewData(Intent())
            loopHandler.postDelayed(refreshLoop, T.mins(interval).msecs())
        }
    }

    override fun advancedFilteringSupported(): Boolean {
        return true
    }

    override fun onStart() {
        super.onStart()
        loopHandler.postDelayed(refreshLoop, T.mins(interval).msecs())
    }

    override fun onStop() {
        super.onStop()
        loopHandler.removeCallbacks(refreshLoop)
    }

    override fun specialEnableCondition(): Boolean {
        return isRunningTest() || virtualPumpPlugin.isEnabled(PluginType.PUMP) && buildHelper.isEngineeringMode()
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        val min = 70
        val max = 190

        val cal = GregorianCalendar()
        val currentMinute = cal.get(Calendar.MINUTE) + (cal.get(Calendar.HOUR_OF_DAY) % 2) * 60
        val bgMgdl = min + ((max - min) + (max - min) * sin(currentMinute / 120.0 * 2 * PI))/2

        val bgReading = BgReading()
        bgReading.value = bgMgdl
        bgReading.date = DateUtil.now()
        bgReading.raw = bgMgdl
        if (MainApp.getDbHelper().createIfNotExists(bgReading, "RandomBG")) {
            if (sp.getBoolean(R.string.key_dexcomg5_nsupload, false))
                nsUpload.uploadBg(bgReading, "AndroidAPS-RandomBG")
            if (sp.getBoolean(R.string.key_dexcomg5_xdripupload, false))
                nsUpload.sendToXdrip(bgReading)
        }
        aapsLogger.debug(LTag.BGSOURCE, "Generated BG: $bgReading")
    }
}
