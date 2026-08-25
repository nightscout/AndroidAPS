package app.aaps.di.metro

import app.aaps.implementation.receivers.BTReceiver
import app.aaps.implementation.receivers.ChargingStateReceiver
import app.aaps.implementation.receivers.NetworkChangeReceiver
import app.aaps.implementation.receivers.TimeDateOrTZChangeReceiver
import app.aaps.persistentNotification.DummyService
import app.aaps.receivers.AutoStartReceiver
import app.aaps.receivers.CarbSuggestionReceiver
import app.aaps.receivers.DataReceiver
import app.aaps.receivers.SmsReceiver
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides
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
 * This is a [GraphExtension] of [AppRootGraph], so it declares only what it adds. Everything it needs -
 * logger, event bus, plugin list, application scope - is bound once in the root and inherited. It used
 * to be a root graph of its own, with a factory restating five dependencies and five functions
 * unwrapping them.
 *
 * Cost per converted class is one `@Provides @IntoMap @ClassKey` line, the same order of work as the
 * `@ContributesAndroidInjector` line it replaces.
 */
@GraphExtension
interface AppReceiversGraph {

    /** Keyed by the class whose fields are filled. [MetroGraphs] dispatches on the runtime type. */
    val memberInjectors: Map<KClass<*>, MembersInjector<*>>

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
    @ClassKey(CarbSuggestionReceiver::class)
    fun bindCarbSuggestionReceiver(injector: MembersInjector<CarbSuggestionReceiver>): MembersInjector<*> = injector

    /** A Service, not a receiver - but the map is just class-keyed, and `MetroService` looks it up the
     * same way. */
    @Provides
    @IntoMap
    @ClassKey(DummyService::class)
    fun bindDummyService(injector: MembersInjector<DummyService>): MembersInjector<*> = injector

    @Provides
    @IntoMap
    @ClassKey(AutoStartReceiver::class)
    fun bindAutoStartReceiver(injector: MembersInjector<AutoStartReceiver>): MembersInjector<*> = injector

    @Provides
    @IntoMap
    @ClassKey(DataReceiver::class)
    fun bindDataReceiver(injector: MembersInjector<DataReceiver>): MembersInjector<*> = injector

    /** SmsReceiver subclasses DataReceiver and adds no fields, but the map is keyed on the RUNTIME
     * class, so it needs its own entry or nothing fills it. */
    @Provides
    @IntoMap
    @ClassKey(SmsReceiver::class)
    fun bindSmsReceiver(injector: MembersInjector<SmsReceiver>): MembersInjector<*> = injector

    @Provides
    @IntoMap
    @ClassKey(TimeDateOrTZChangeReceiver::class)
    fun bindTimeDateOrTZChangeReceiver(
        injector: MembersInjector<TimeDateOrTZChangeReceiver>
    ): MembersInjector<*> = injector
}
