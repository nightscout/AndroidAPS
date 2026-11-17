package app.aaps.plugins.constraints.dstHelper

import app.aaps.core.data.model.RM
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.dst.DstHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.EnforcedState
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.constraints.ConstraintsStrings
import app.aaps.plugins.constraints.dstHelper.keys.DstHelperLongKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * For pumps that cannot handle a clock change: warns 12 hours before a change of the phone's time zone offset, and
 * suspends the loop for 3 hours after one.
 */
@ContributesBinding(AppScope::class, binding = binding<DstHelper>())
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(850)
@SingleIn(AppScope::class)
@Inject
class DstHelperPlugin(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    preferences: Preferences,
    private val activePlugin: ActivePlugin,
    notificationManager: NotificationManager,
    private val loop: Loop,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.GENERAL)
        .enforce(EnforcedState.Enabled)
        .showInList { false }
        .pluginName(ConstraintsStrings.dst_plugin_name),
    ownPreferences = DstHelperLongKey.entries,
    aapsLogger, rh, preferences, notificationManager
), DstHelper {

    companion object {

        private const val DISABLE_TIME_FRAME_HOURS = -3
        private const val WARN_PRIOR_TIME_FRAME_HOURS = 12
    }

    //Return false if time to DST change happened in the last 3 hours.
    override fun dstCheck() {
        val pump = activePlugin.activePump
        if (pump.canHandleDST()) return
        val now = dateUtil.now()
        val zone = TimeZone.currentSystemDefault()
        if (willBeDST(now, zone)) {
            val snoozedTo: Long = preferences.get(DstHelperLongKey.SnoozeDstIn24h)
            if (snoozedTo == 0L || dateUtil.now() > snoozedTo) {
                notificationManager.post(
                    NotificationId.DST_IN_24H,
                    ConstraintsStrings.dst_in_24h_warning,
                    actions = listOf(NotificationAction(CoreUiStrings.snooze) {
                        preferences.put(DstHelperLongKey.SnoozeDstIn24h, dateUtil.now() + T.hours(24).msecs())
                    }))
            }
        }
        if (wasDST(now, zone)) {
            // dstCheck() is invoked from KeepAliveRunner on a background scope; the file
            // already uses runBlocking for profileFunction. Keeping the same pattern here.
            val mode = runBlocking { loop.runningMode() }
            if (!mode.pausesLoopExecution()) {
                val profile = runBlocking { profileFunction.getProfile() } ?: return
                runBlocking {
                    loop.handleRunningModeChange(newRM = RM.Mode.SUSPENDED_BY_DST, durationInMinutes = T.hours((-DISABLE_TIME_FRAME_HOURS).toLong()).mins().toInt(), action = Action.SUSPEND, source = Sources.Aaps, profile = profile)
                }
                val snoozedTo: Long = preferences.get(DstHelperLongKey.SnoozeLoopDisabled)
                if (snoozedTo == 0L || dateUtil.now() > snoozedTo) {
                    notificationManager.post(
                        NotificationId.DST_LOOP_DISABLED,
                        ConstraintsStrings.dst_loop_disabled_warning,
                        actions = listOf(NotificationAction(CoreUiStrings.snooze) {
                            preferences.put(DstHelperLongKey.SnoozeLoopDisabled, dateUtil.now() + T.hours(24).msecs())
                        }))
                }
            } else {
                aapsLogger.debug(LTag.CONSTRAINTS, "Loop already suspended")
            }
        }
    }

    /** Whether the offset of [zone] changed in the 3 hours before [now]. */
    internal fun wasDST(now: Long, zone: TimeZone): Boolean = offsetChanges(now, DISABLE_TIME_FRAME_HOURS, zone)

    /** Whether the offset of [zone] changes in the 12 hours after [now]. */
    internal fun willBeDST(now: Long, zone: TimeZone): Boolean = offsetChanges(now, WARN_PRIOR_TIME_FRAME_HOURS, zone)

    /**
     * Whether the offset of [zone] from UTC at [now] is different [hours] later, or earlier when [hours] is negative.
     *
     * The whole offset is compared, not only the summer time part as `Calendar.DST_OFFSET` did. They differ only when a
     * zone changes its standard time, which moves the phone clock against the pump clock just as summer time does.
     */
    private fun offsetChanges(now: Long, hours: Int, zone: TimeZone): Boolean {
        val instant = Instant.fromEpochMilliseconds(now)
        return zone.offsetAt(instant) != zone.offsetAt(instant + hours.hours)
    }
}
