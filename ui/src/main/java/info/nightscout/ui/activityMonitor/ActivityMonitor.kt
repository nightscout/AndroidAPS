package info.nightscout.ui.activityMonitor

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.SafeParse
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import info.nightscout.ui.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityMonitor @Inject constructor(
    private var aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val dateUtil: DateUtil
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityPaused(activity: Activity) {
        val name = activity.javaClass.simpleName
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

    override fun onActivityResumed(activity: Activity) {
        val name = activity.javaClass.simpleName
        aapsLogger.debug(LTag.UI, "onActivityResumed: $name")
        sp.putLong("Monitor_" + name + "_" + "resumed", dateUtil.now())
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    fun stats(context: Context): TableLayout =
        TableLayout(context).also { layout ->
            layout.layoutParams = TableLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layout.addView(
                TextView(context).apply {
                    text = rh.gs(R.string.activity_monitor)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER_HORIZONTAL
                    setTextAppearance(android.R.style.TextAppearance_Material_Medium)
                })
            layout.addView(
                TableRow(context).also { row ->
                    val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
                    row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
                    row.gravity = Gravity.CENTER_HORIZONTAL
                    row.addView(TextView(context).apply { layoutParams = lp.apply { column = 0 }; text = rh.gs(info.nightscout.core.ui.R.string.activity) })
                    row.addView(TextView(context).apply { layoutParams = lp.apply { column = 1 }; text = rh.gs(info.nightscout.core.ui.R.string.duration) })
                    row.addView(TextView(context).apply { layoutParams = lp.apply { column = 2 } })
                }
            )

            val keys: Map<String, *> = sp.getAll()
            for ((key, value) in keys)
                if (key.startsWith("Monitor") && key.endsWith("total")) {
                    val v = if (value is Long) value else SafeParse.stringToLong(value as String)
                    val activity = key.split("_")[1].replace("Activity", "")
                    val duration = dateUtil.niceTimeScalar(v, rh)
                    val start = sp.getLong(key.replace("total", "start"), 0)
                    val days = T.msecs(dateUtil.now() - start).days()
                    layout.addView(
                        TableRow(context).also { row ->
                            val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
                            row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
                            row.gravity = Gravity.CENTER_HORIZONTAL
                            row.addView(TextView(context).apply { layoutParams = lp.apply { column = 0 }; text = activity })
                            row.addView(TextView(context).apply { layoutParams = lp.apply { column = 1 }; text = duration })
                            row.addView(TextView(context).apply { layoutParams = lp.apply { column = 2 }; text = rh.gs(info.nightscout.interfaces.R.string.in_days, days.toDouble()) })
                        }
                    )
                }
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