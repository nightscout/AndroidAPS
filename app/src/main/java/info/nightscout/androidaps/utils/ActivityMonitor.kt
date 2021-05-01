package info.nightscout.androidaps.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.text.Spanned
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityMonitor @Inject constructor(
    private var aapsLogger: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val dateUtil: DateUtil
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityPaused(activity: Activity?) {
        val name = activity?.javaClass?.simpleName ?: return
        val resumed = sp.getLong("Monitor_" + name + "_" + "resumed", 0)
        if (resumed == 0L) {
            aapsLogger.debug(LTag.UI, "onActivityPaused: $name resumed == 0")
            return
        }
        val elapsed = dateUtil.now() - resumed
        val total = sp.getLong("Monitor_" + name + "_total", 0)
        if (total == 0L) {
            sp.putLong("Monitor_" + name + "_start", dateUtil.now())
        }
        sp.putLong("Monitor_" + name + "_total", total + elapsed)
        aapsLogger.debug(LTag.UI, "onActivityPaused: $name elapsed=$elapsed total=${total + elapsed}")
    }

    override fun onActivityResumed(activity: Activity?) {
        val name = activity?.javaClass?.simpleName ?: return
        aapsLogger.debug(LTag.UI, "onActivityResumed: $name")
        sp.putLong("Monitor_" + name + "_" + "resumed", dateUtil.now())
    }

    override fun onActivityStarted(activity: Activity?) {
    }

    override fun onActivityDestroyed(activity: Activity?) {
    }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
    }

    override fun onActivityStopped(activity: Activity?) {
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
    }

    private fun toText(): String {
        val keys: Map<String, *> = sp.getAll()
        var result = ""
        for ((key, value) in keys)
            if (key.startsWith("Monitor") && key.endsWith("total")) {
                val v = if (value is Long) value else SafeParse.stringToLong(value as String)
                val activity = key.split("_")[1].replace("Activity", "")
                val duration = dateUtil.niceTimeScalar(v as Long, resourceHelper)
                val start = sp.getLong(key.replace("total", "start"), 0)
                val days = T.msecs(dateUtil.now() - start).days()
                result += resourceHelper.gs(R.string.activitymonitorformat, activity, duration, days)
            }
        return result
    }

    fun stats(): Spanned {
        return HtmlHelper.fromHtml("<br><b>" + resourceHelper.gs(R.string.activitymonitor) + ":</b><br>" + toText())
    }

    fun reset() {
        val keys: Map<String, *> = sp.getAll()
        for ((key, _) in keys)
            if (key.startsWith("Monitor") && key.endsWith("total")) {
                sp.remove(key)
                sp.remove(key.replace("total", "start"))
                sp.remove(key.replace("total", "resumed"))
            }
    }
}