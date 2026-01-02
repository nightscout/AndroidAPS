package app.aaps.plugins.constraints.dstHelper

import app.aaps.core.data.model.RM
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.dstHelper.keys.DstHelperLongKey
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DstHelperPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val activePlugin: ActivePlugin,
    private val uiInteraction: UiInteraction,
    private val loop: Loop,
    private val profileFunction: ProfileFunction
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.GENERAL)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList { false }
        .pluginName(R.string.dst_plugin_name),
    ownPreferences = listOf(DstHelperLongKey::class.java),
    aapsLogger, rh, preferences
) {

    companion object {

        private const val DISABLE_TIME_FRAME_HOURS = -3
        private const val WARN_PRIOR_TIME_FRAME_HOURS = 12
    }

    //Return false if time to DST change happened in the last 3 hours.
    fun dstCheck() {
        val pump = activePlugin.activePump
        if (pump.canHandleDST()) return
        val cal = Calendar.getInstance()
        if (willBeDST(cal)) {
            val snoozedTo: Long = preferences.get(DstHelperLongKey.SnoozeDstIn24h)
            if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                uiInteraction.addNotification(Notification.DST_IN_24H, rh.gs(R.string.dst_in_24h_warning), Notification.LOW, app.aaps.core.ui.R.string.snooze, {
                    preferences.put(DstHelperLongKey.SnoozeDstIn24h, System.currentTimeMillis() + T.hours(24).msecs())
                }, null)
            }
        }
        if (wasDST(cal)) {
            if (!loop.runningMode.isSuspended()) {
                val profile = profileFunction.getProfile() ?: return
                loop.handleRunningModeChange(newRM = RM.Mode.SUSPENDED_BY_DST, durationInMinutes = T.hours((-DISABLE_TIME_FRAME_HOURS).toLong()).mins().toInt(), action = Action.SUSPEND, source = Sources.Aaps, profile = profile)
                val snoozedTo: Long = preferences.get(DstHelperLongKey.SnoozeLoopDisabled)
                if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                    uiInteraction.addNotification(
                        id = Notification.DST_LOOP_DISABLED,
                        text = rh.gs(R.string.dst_loop_disabled_warning),
                        level = Notification.LOW,
                        actionButtonId = app.aaps.core.ui.R.string.snooze,
                        action = { preferences.put(DstHelperLongKey.SnoozeLoopDisabled, System.currentTimeMillis() + T.hours(24).msecs()) },
                        validityCheck = null
                    )
                }
            } else {
                aapsLogger.debug(LTag.CONSTRAINTS, "Loop already suspended")
            }
        }
    }

    internal fun wasDST(now: Calendar): Boolean {
        val ago = now.clone() as Calendar
        ago.add(Calendar.HOUR, DISABLE_TIME_FRAME_HOURS)
        return now[Calendar.DST_OFFSET] != ago[Calendar.DST_OFFSET]
    }

    internal fun willBeDST(now: Calendar): Boolean {
        val ago = now.clone() as Calendar
        ago.add(Calendar.HOUR, WARN_PRIOR_TIME_FRAME_HOURS)
        return now[Calendar.DST_OFFSET] != ago[Calendar.DST_OFFSET]
    }
}