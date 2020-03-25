package info.nightscout.androidaps.plugins.source

import android.content.Intent
import android.os.Handler
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.isRunningTest
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.math.PI
import kotlin.math.sin

object RandomBgPlugin : PluginBase(PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginName(R.string.randombg)
        .shortName(R.string.randombg_short)
        .description(R.string.description_source_randombg)), BgSourceInterface {

    private val log = LoggerFactory.getLogger(L.BGSOURCE)

    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    const val interval = 1L // minutes

    init {
        refreshLoop = Runnable {
            handleNewData(Intent())
            loopHandler.postDelayed(refreshLoop, T.mins(interval).msecs())
        }
    }

    override fun advancedFilteringSupported(): Boolean {
        return false
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
        return isRunningTest() || VirtualPumpPlugin.getPlugin().isEnabled(PluginType.PUMP) && MainApp.engineeringMode
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        val min = 70
        val max = 190

        val cal = GregorianCalendar()
        val currentMinute = cal.get(Calendar.MINUTE) + (cal.get(Calendar.HOUR_OF_DAY) % 2) * 60
        val bgMgdl = min + (max - min) + (max - min) * sin(currentMinute / 120.0 * 2 * PI)

        val bgReading = BgReading()
        bgReading.value = bgMgdl
        bgReading.date = DateUtil.now()
        bgReading.raw = bgMgdl
        MainApp.getDbHelper().createIfNotExists(bgReading, "RandomBG")
        log.debug("Generated BG: $bgReading")
    }
}
