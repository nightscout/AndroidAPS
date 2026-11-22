package app.aaps.plugins.automation

import app.aaps.core.interfaces.alerts.ReminderScheduler
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The Alarm action of automation as a booked alert ([UiInteraction.scheduleAlarm], `_docs/ALARMS.md` step 1e), on
 * Android and iOS. The platform rings it at the time also when the app does not run: an exact alarm on Android, a
 * notification on iOS, where the real alert with its card and alarm screen follows when the app runs. It used to be
 * an Android receiver of its own, and on iOS only a notification.
 *
 * All reminders share one key, so a second one booked before the first rang moves it, with its own text: they all
 * say "the timer you set has run out", and a card the user has to answer twice would only be noise.
 *
 * Bound by each platform that has alerts; the desktop has no alert core and keeps its own.
 */
@SingleIn(AppScope::class)
@Inject
class AlertReminderScheduler(
    private val uiInteraction: UiInteraction,
    private val rh: TextResolver,
    private val rxBus: RxBus,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : ReminderScheduler {

    override fun scheduleReminder(seconds: Int, text: String) {
        val appName = rh.gs(config.appName)
        val booked = uiInteraction.scheduleAlarm(
            at = dateUtil.now() + seconds * 1000L,
            key = UiInteraction.KEY_AUTOMATION_ALARM,
            status = text.takeIf { it.isNotBlank() } ?: appName,
            title = appName,
            sound = AlarmSound.ALARM
        )
        if (!booked) {
            // Android without exact alarms; PluginPermissionsImpl asks for them
            aapsLogger.warn(LTag.AUTOMATION, "Reminder \"$text\" not set: its alarm could not be booked")
            rxBus.send(EventShowSnackbar(rh.gs(AutomationStrings.error_setting_reminder), EventShowSnackbar.Type.Error))
        }
    }
}
