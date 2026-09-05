package app.aaps.ui.activityMonitor

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class, binding = binding<ActivityStatsProvider>())
@SingleIn(AppScope::class)
class ActivityMonitor @Inject constructor(
    private var aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val preferences: Preferences,
    private val dateUtil: DateUtil
) : Application.ActivityLifecycleCallbacks, ActivityStatsProvider {

    override fun onActivityPaused(activity: Activity) {
        val name = activity::class.simpleName.orEmpty()
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
        val name = activity::class.simpleName.orEmpty()
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

    override fun getActivityStats(): List<ActivityStats> {
        return preferences.allMatchingStrings(LongComposedKey.ActivityMonitorTotal).map { activityName ->
            val v = preferences.get(LongComposedKey.ActivityMonitorTotal, activityName)
            val activity = activityName.replace("Activity", "")
            val duration = dateUtil.niceTimeScalar(v, rh)
            val start = preferences.get(LongComposedKey.ActivityMonitorStart, activityName)
            val days = T.msecs(dateUtil.now() - start).days()
            ActivityStats(
                activityName = activity,
                duration = duration,
                days = days.toDouble()
            )
        }
    }

    override fun reset() {
        preferences.allMatchingStrings(LongComposedKey.ActivityMonitorTotal).forEach { activityName ->
            preferences.remove(LongComposedKey.ActivityMonitorTotal, activityName)
            preferences.remove(LongComposedKey.ActivityMonitorStart, activityName)
            preferences.remove(LongComposedKey.ActivityMonitorResumed, activityName)
        }
    }
}