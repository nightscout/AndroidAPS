package app.aaps.plugins.automation.di

import app.aaps.plugins.automation.TimerReminderReceiver
import app.aaps.plugins.automation.services.LocationService
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides
import kotlin.reflect.KClass

/**
 * Scope marker for this module's automation wiring.
 *
 * The graph is an extension with a scope of its own, not a second root on `AppScope`: two graphs
 * declaring one scope each get their own copy of anything scoped there, silently. `AppScope` means
 * exactly one graph.
 */
abstract class AutomationScope private constructor()

/**
 * Metro wiring for automation's two Android entry points.
 * Automation still reaches both through interfaces in `:core:interfaces` commonMain
 * (`LocationServiceController`, `ReminderScheduler`), so an iOS build can supply its own
 * implementations without touching plugin code. That part of the earlier design was right and is
 * kept.
 */
@GraphExtension(AutomationScope::class)
interface AutomationMetroGraph {

    /** Fills the fields of the two entry points - the `@ContributesAndroidInjector` replacement. */
    val memberInjectors: Map<KClass<*>, MembersInjector<*>>


    @Provides
    @IntoMap
    @ClassKey(TimerReminderReceiver::class)
    fun bindTimerReminderReceiver(injector: MembersInjector<TimerReminderReceiver>): MembersInjector<*> = injector

    @Provides
    @IntoMap
    @ClassKey(LocationService::class)
    fun bindLocationService(injector: MembersInjector<LocationService>): MembersInjector<*> = injector
}
