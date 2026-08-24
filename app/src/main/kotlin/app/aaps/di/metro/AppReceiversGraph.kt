package app.aaps.di.metro

import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.implementation.receivers.BTReceiver
import app.aaps.implementation.receivers.ChargingStateReceiver
import app.aaps.implementation.receivers.NetworkChangeReceiver
import app.aaps.implementation.receivers.TimeDateOrTZChangeReceiver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

/**
 * Metro wiring for Android classes that inject their own fields - the `@ContributesAndroidInjector`
 * replacement, and the biggest of the four categories at 294 sites.
 *
 * Android constructs receivers, services and activities itself, so they cannot take dependencies in a
 * constructor. dagger.android answers this with `DispatchingAndroidInjector`, a runtime map looked up
 * by class. The map below is the same idea built at compile time out of Metro's own [MembersInjector],
 * so a class that is registered but has an unsatisfiable dependency fails the build rather than the
 * launch.
 *
 * Cost per converted class is one `@Provides @IntoMap @ClassKey` line, the same order of work as the
 * `@ContributesAndroidInjector` line it replaces.
 */
@DependencyGraph(AppScope::class)
interface AppReceiversGraph {

    /** Keyed by the class whose fields are filled. [MetroGraphs] dispatches on the runtime type. */
    val memberInjectors: Map<KClass<*>, MembersInjector<*>>

    @DependencyGraph.Factory
    fun interface Factory {

        fun create(
            @Provides aapsLoggerRef: DeferredRef<AAPSLogger>,
            @Provides receiverStatusStoreRef: DeferredRef<ReceiverStatusStore>,
            @Provides rxBusRef: DeferredRef<RxBus>,
            @Provides activePluginRef: DeferredRef<ActivePlugin>,
            @Provides appScopeRef: DeferredRef<CoroutineScope>
        ): AppReceiversGraph
    }

    @Provides fun aapsLogger(r: DeferredRef<AAPSLogger>): AAPSLogger = r.get()
    @Provides fun receiverStatusStore(r: DeferredRef<ReceiverStatusStore>): ReceiverStatusStore = r.get()
    @Provides fun rxBus(r: DeferredRef<RxBus>): RxBus = r.get()
    @Provides fun activePlugin(r: DeferredRef<ActivePlugin>): ActivePlugin = r.get()
    // The application scope. Only one CoroutineScope lives in this graph, which is what lets
    // TimeDateOrTZChangeReceiver ask for it without a qualifier.
    @Provides fun appScope(r: DeferredRef<CoroutineScope>): CoroutineScope = r.get()

    @Provides
    @IntoMap
    @ClassKey(ChargingStateReceiver::class)
    fun bindChargingStateReceiver(injector: MembersInjector<ChargingStateReceiver>): MembersInjector<*> = injector

    @Provides
    @IntoMap
    @ClassKey(NetworkChangeReceiver::class)
    fun bindNetworkChangeReceiver(injector: MembersInjector<NetworkChangeReceiver>): MembersInjector<*> = injector

    @Provides
    @IntoMap
    @ClassKey(BTReceiver::class)
    fun bindBTReceiver(injector: MembersInjector<BTReceiver>): MembersInjector<*> = injector

    @Provides
    @IntoMap
    @ClassKey(TimeDateOrTZChangeReceiver::class)
    fun bindTimeDateOrTZChangeReceiver(
        injector: MembersInjector<TimeDateOrTZChangeReceiver>
    ): MembersInjector<*> = injector
}
