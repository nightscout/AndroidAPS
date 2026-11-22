package app.aaps.implementation.alerts

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The "time to eat" alarm on iOS: a booked alert ([UiInteraction.scheduleEatReminderAlarm], `_docs/ALARMS.md`
 * step 1e). iOS rings its notification at the end also when the app does not run, and the core raises the real alert,
 * with its card, alarm screen and +5 min, when the app runs. It used to be only a notification.
 *
 * The full app sets it; a client never does, because the master owns the timer. iOS has no countdown notification.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosEatReminderAlarm(
    private val rh: TextResolver,
    private val config: Config,
    // A provider: UiInteraction reaches the timer back for its +5 min.
    private val uiInteraction: () -> UiInteraction,
    private val aapsLogger: AAPSLogger
) : EatReminderAlarm {

    override fun schedule(startedAt: Long, endsAt: Long): Boolean {
        val booked = uiInteraction().scheduleEatReminderAlarm(endsAt, status = rh.gs(CoreUiStrings.eat_reminder_title), title = rh.gs(config.appName))
        if (!booked) aapsLogger.error(LTag.AUTOMATION, "Eat reminder not set: its alarm could not be booked")
        return booked
    }

    override fun cancel() = uiInteraction().cancelScheduledAlarm(UiInteraction.KEY_EAT_REMINDER)
}
