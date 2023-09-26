package app.aaps.plugins.constraints.dstHelper

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.T
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.constraints.R
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DstHelperPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val sp: SP,
    private val activePlugin: ActivePlugin,
    private val loop: Loop
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList(false)
        .pluginName(R.string.dst_plugin_name),
    aapsLogger, rh, injector
), PluginConstraints {

    companion object {

        private const val DISABLE_TIME_FRAME_HOURS = -3
        private const val WARN_PRIOR_TIME_FRAME_HOURS = 12
    }

    //Return false if time to DST change happened in the last 3 hours.
    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val pump = activePlugin.activePump
        if (pump.canHandleDST()) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Pump can handle DST")
            return value
        }
        val cal = Calendar.getInstance()
        if (willBeDST(cal)) {
            val snoozedTo: Long = sp.getLong(R.string.key_snooze_dst_in24h, 0L)
            if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                activePlugin.activeOverview.addNotification(Notification.DST_IN_24H, rh.gs(R.string.dst_in_24h_warning), Notification.LOW, app.aaps.core.ui.R.string.snooze) {
                    sp.putLong(R.string.key_snooze_dst_in24h, System.currentTimeMillis() + T.hours(24).msecs())
                }
            }
        }
        if (!value.value()) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Already not allowed - don't check further")
            return value
        }
        if (wasDST(cal)) {
            if (!loop.isSuspended) {
                val snoozedTo: Long = sp.getLong(R.string.key_snooze_loop_disabled, 0L)
                if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                    activePlugin.activeOverview.addNotification(Notification.DST_LOOP_DISABLED, rh.gs(R.string.dst_loop_disabled_warning), Notification.LOW, app.aaps.core.ui.R.string.snooze) {
                        sp.putLong(R.string.key_snooze_loop_disabled, System.currentTimeMillis() + T.hours(24).msecs())
                    }
                }
            } else {
                aapsLogger.debug(LTag.CONSTRAINTS, "Loop already suspended")
            }
            value.set(false, "DST in last 3 hours.", this)
        }
        return value
    }

    fun wasDST(now: Calendar): Boolean {
        val ago = now.clone() as Calendar
        ago.add(Calendar.HOUR, DISABLE_TIME_FRAME_HOURS)
        return now[Calendar.DST_OFFSET] != ago[Calendar.DST_OFFSET]
    }

    fun willBeDST(now: Calendar): Boolean {
        val ago = now.clone() as Calendar
        ago.add(Calendar.HOUR, WARN_PRIOR_TIME_FRAME_HOURS)
        return now[Calendar.DST_OFFSET] != ago[Calendar.DST_OFFSET]
    }
}