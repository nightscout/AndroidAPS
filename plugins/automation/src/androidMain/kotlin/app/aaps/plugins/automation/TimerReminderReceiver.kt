package app.aaps.plugins.automation

import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import dev.zacsweers.metro.Inject

/**
 * Rings the AAPS full-screen alarm for a reminder previously scheduled by
 * [app.aaps.core.interfaces.alerts.ReminderScheduler] via AlarmManager.
 *
 * Why this exists: handing a reminder to the system Clock app with `startActivity(ACTION_SET_TIMER)`
 * does not work, because Android blocks that from the background. A reminder scheduled while AAPS is
 * backgrounded — e.g. a Bolus-Wizard "Set alarm" delivered ON THE MASTER after being relayed from a
 * client, which the master runs in a background service — would be silently dropped. AlarmManager
 * delivers this broadcast in the background (idle-exempt via `setAlarmClock`) and
 * [UiInteraction.runAlarm] posts the full-screen-intent alarm without needing a foreground activity,
 * so the reminder rings regardless of state.
 */
class TimerReminderReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var config: Config
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: rh.gs(config.appName)
        aapsLogger.debug(LTag.AUTOMATION, "TimerReminderReceiver fired: $text")
        uiInteraction.runAlarm(status = text, title = rh.gs(config.appName), sound = AlarmSound.ALARM)
    }

    companion object {

        const val EXTRA_TEXT = "app.aaps.plugins.automation.timerReminder.text"
    }
}
