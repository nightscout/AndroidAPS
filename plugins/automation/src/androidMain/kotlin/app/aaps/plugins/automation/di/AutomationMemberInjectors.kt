package app.aaps.plugins.automation.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.plugins.automation.TimerReminderReceiver
import app.aaps.plugins.automation.services.LocationService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Members injectors for the two Android classes this module lets the framework construct.
 *
 * Contributed into the root's one `@FeatureMemberInjectors` map, the same way every pump module does
 * it. This used to be a graph extension carrying a map and a scope of its own, which meant `:app` had
 * to name it in `AppRootGraph` and then search a fourth map to find an injector. The scope was never
 * used by anything - nothing was scoped to it - so it went with the extension.
 *
 * A class the framework constructs and this module forgets to list here gets no injected fields, so
 * add the line at the same time as the receiver or service.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AutomationMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(TimerReminderReceiver::class)
    fun bindTimerReminderReceiver(injector: MembersInjector<TimerReminderReceiver>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(LocationService::class)
    fun bindLocationService(injector: MembersInjector<LocationService>): MembersInjector<*> = injector
}
