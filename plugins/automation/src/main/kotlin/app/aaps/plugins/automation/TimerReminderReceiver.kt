package app.aaps.plugins.automation

import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import dagger.android.DaggerBroadcastReceiver
import javax.inject.Inject
import app.aaps.core.ui.R as CoreUiR

/**
 * Rings the AAPS full-screen alarm for a reminder previously scheduled by [TimerUtil] via AlarmManager.
 *
 * Why this exists: reminders ("time to eat", automation Alarm action, …) used to be handed to the system Clock
 * app with `startActivity(ACTION_SET_TIMER)`, which Android blocks from the background. So a reminder scheduled
 * while AAPS is backgrounded — e.g. a Bolus-Wizard "Set alarm" that is delivered ON THE MASTER after being relayed
 * from a client (the master runs that in a background service) — was silently dropped. AlarmManager delivers this
 * broadcast in the background (idle-exempt via `setAlarmClock`) and [UiInteraction.runAlarm] posts the
 * full-screen-intent alarm without needing a foreground activity, so the reminder now rings regardless of state.
 */
class TimerReminderReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var config: Config
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: rh.gs(config.appName)
        aapsLogger.debug(LTag.AUTOMATION, "TimerReminderReceiver fired: $text")
        uiInteraction.runAlarm(status = text, title = rh.gs(config.appName), soundId = CoreUiR.raw.alarm)
    }

    companion object {

        const val EXTRA_TEXT = "app.aaps.plugins.automation.timerReminder.text"
    }
}
