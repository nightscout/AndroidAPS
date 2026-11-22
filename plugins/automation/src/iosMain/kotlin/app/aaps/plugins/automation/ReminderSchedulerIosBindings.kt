package app.aaps.plugins.automation

import app.aaps.core.interfaces.alerts.ReminderScheduler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** iOS has alerts, so a reminder is a booked alert. */
@ContributesTo(AppScope::class)
@BindingContainer
object ReminderSchedulerIosBindings {

    @Provides
    fun reminderScheduler(impl: AlertReminderScheduler): ReminderScheduler = impl
}
