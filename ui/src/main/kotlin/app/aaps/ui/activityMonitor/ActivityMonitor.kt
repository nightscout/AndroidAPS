package app.aaps.ui.activityMonitor

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
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityMonitor @Inject constructor(
    private var aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val dateUtil: DateUtil
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityPaused(activity: Activity) {
        val name = activity.javaClass.simpleName
        val resumed = preferences.get(LongComposedKey.ActivityMonitorResumed, name)
        if (resumed == 0L) {
            aapsLogger.debug(LTag.UI, "onActivityPaused: $name resumed == 0")
            return
        }
        val elapsed = dateUtil.now() - resumed
        val total = preferences.get(LongComposedKey.ActivityMonitorTotal, name)
        if (total == 0L) {
            preferences.put(LongComposedKey.ActivityMonitorStart, name, value = dateUtil.now())
        }
        preferences.put(LongComposedKey.ActivityMonitorTotal, name, value = total + elapsed)
        aapsLogger.debug(LTag.UI, "onActivityPaused: $name elapsed=$elapsed total=${total + elapsed}")
    }

    override fun onActivityResumed(activity: Activity) {
        val name = activity.javaClass.simpleName
        aapsLogger.debug(LTag.UI, "onActivityResumed: $name")
        preferences.put(LongComposedKey.ActivityMonitorResumed, name, value = dateUtil.now())
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
                    row.addView(TextView(context).apply { layoutParams = lp.apply { column = 0 }; text = rh.gs(app.aaps.core.ui.R.string.activity) })
                    row.addView(TextView(context).apply { layoutParams = lp.apply { column = 1 }; text = rh.gs(app.aaps.core.ui.R.string.duration) })
                    row.addView(TextView(context).apply { layoutParams = lp.apply { column = 2 } })
                }
            )

            preferences.allMatchingStrings(LongComposedKey.ActivityMonitorTotal).forEach { activityName ->
                val v = preferences.get(LongComposedKey.ActivityMonitorTotal, activityName)
                val activity = activityName.replace("Activity", "")
                val duration = dateUtil.niceTimeScalar(v, rh)
                val start = preferences.get(LongComposedKey.ActivityMonitorStart, activityName)
                val days = T.msecs(dateUtil.now() - start).days()
                layout.addView(
                    TableRow(context).also { row ->
                        val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
                        row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
                        row.gravity = Gravity.CENTER_HORIZONTAL
                        row.addView(TextView(context).apply { layoutParams = lp.apply { column = 0 }; text = activity })
                        row.addView(TextView(context).apply { layoutParams = lp.apply { column = 1 }; text = duration })
                        row.addView(TextView(context).apply { layoutParams = lp.apply { column = 2 }; text = rh.gs(app.aaps.core.interfaces.R.string.in_days, days.toDouble()) })
                    }
                )
            }
        }

    fun reset() {
        preferences.allMatchingStrings(LongComposedKey.ActivityMonitorTotal).forEach { activityName ->
            preferences.remove(LongComposedKey.ActivityMonitorTotal, activityName)
            preferences.remove(LongComposedKey.ActivityMonitorStart, activityName)
            preferences.remove(LongComposedKey.ActivityMonitorResumed, activityName)
        }
    }
}