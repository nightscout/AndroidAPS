package app.aaps.pump.ap550s.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.pump.ap550s.receiver.AP550KeepAliveReceiver
import app.aaps.pump.ap550s.service.AP550ExecutionService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Members injectors for the classes in this driver that the Android framework constructs, so Metro
 * can still fill their `@Inject` fields.
 *
 * Keyed by class, which is how `MetroMemberInjector` finds the right one at runtime. A driver that
 * adds such a class has to add it here too - nothing else knows about it.
 *
 * The service uses `MetroService` and the receiver uses `MetroBroadcastReceiver`, so the entries here
 * only tell the map which injector belongs to them. The plugin, the pump, the history DAO and the
 * helper objects are built by the graph itself and need no entry.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AP550MemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AP550ExecutionService::class)
    fun bindAP550ExecutionService(injector: MembersInjector<AP550ExecutionService>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AP550KeepAliveReceiver::class)
    fun bindAP550KeepAliveReceiver(injector: MembersInjector<AP550KeepAliveReceiver>): MembersInjector<*> = injector
}