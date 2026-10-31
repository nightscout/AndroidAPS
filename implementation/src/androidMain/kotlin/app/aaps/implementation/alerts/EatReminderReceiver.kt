package app.aaps.implementation.alerts

import android.content.Context
import android.content.Intent
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.Inject

/**
 * Everything Android sends to the "time to eat" timer: the alarm at the end ([ACTION_RING]) and the Stop and
 * +5 min buttons of its notifications ([ACTION_STOP], [ACTION_EXTEND]). +5 min on the ringing alarm is the same
 * action: [EatReminder.extend] silences the alarm when the timer had rung.
 *
 * A manifest receiver is not injected by the system, so it asks the graph itself, like `AlarmMuteReceiver`.
 */
class EatReminderReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var eatReminder: EatReminder
    @Inject lateinit var alarm: AndroidEatReminderAlarm
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var config: Config
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        aapsLogger.debug(LTag.AUTOMATION, "EatReminderReceiver: ${intent.action}")
        when (intent.action) {
            ACTION_RING   -> ring()
            ACTION_STOP   -> eatReminder.stop()
            ACTION_EXTEND -> eatReminder.extend()
        }
    }

    private fun ring() {
        alarm.cancelCountdown()
        // Only for the timer that is set now. One that was stopped, or moved later, has nothing to ring yet.
        val state = eatReminder.state.value
        if (state == null || state.endsAt - dateUtil.now() > RING_TOLERANCE_MS) {
            aapsLogger.debug(LTag.AUTOMATION, "Eat reminder alarm ignored: no timer due now")
            return
        }
        // First, so that stopping this alarm (OK, Mute, the watch) is known to end this timer.
        eatReminder.onRang()
        uiInteraction.runEatReminderAlarm(status = rh.gs(CoreUiStrings.eat_reminder_title), title = rh.gs(config.appName))
    }

    companion object {

        const val ACTION_RING = "app.aaps.implementation.eatReminder.RING"
        const val ACTION_STOP = "app.aaps.implementation.eatReminder.STOP"
        const val ACTION_EXTEND = "app.aaps.implementation.eatReminder.EXTEND"

        /** An alarm may come a little early; one more than this before the end belongs to an older timer. */
        private val RING_TOLERANCE_MS = T.mins(1).msecs()

        /** Separate request codes, so the buttons' intents do not replace each other. */
        fun requestCodeOf(action: String): Int = when (action) {
            ACTION_STOP -> 4721
            else        -> 4722
        }
    }
}
