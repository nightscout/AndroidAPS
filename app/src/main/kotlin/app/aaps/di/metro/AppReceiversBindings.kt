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
import app.aaps.core.interfaces.di.FeatureMemberInjectors
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Metro wiring for Android classes that inject their own fields - the `@ContributesAndroidInjector`
 * replacement, and the biggest of the four categories at 294 sites.
 * Contributed into the root's one `@FeatureMemberInjectors` map, the same way every pump module does
 * it - see `DanaRSMemberInjectors`. It used to be a graph extension with a map of its own, which meant
 * `MetroGraphs` had to name it and then search four maps in turn to find an injector. One map is one
 * lookup, and a module can add to it without anything in `:app` knowing.
 * Cost per converted class is one `@Provides @IntoMap @ClassKey` line, the same order of work as the
 * `@ContributesAndroidInjector` line it replaces.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AppReceiversBindings {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ChargingStateReceiver::class)
    fun bindChargingStateReceiver(injector: MembersInjector<ChargingStateReceiver>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(NetworkChangeReceiver::class)
    fun bindNetworkChangeReceiver(injector: MembersInjector<NetworkChangeReceiver>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BTReceiver::class)
    fun bindBTReceiver(injector: MembersInjector<BTReceiver>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CarbSuggestionReceiver::class)
    fun bindCarbSuggestionReceiver(injector: MembersInjector<CarbSuggestionReceiver>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(DummyService::class)
    fun bindDummyService(injector: MembersInjector<DummyService>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AutoStartReceiver::class)
    fun bindAutoStartReceiver(injector: MembersInjector<AutoStartReceiver>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(DataReceiver::class)
    fun bindDataReceiver(injector: MembersInjector<DataReceiver>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SmsReceiver::class)
    fun bindSmsReceiver(injector: MembersInjector<SmsReceiver>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(TimeDateOrTZChangeReceiver::class)
    fun bindTimeDateOrTZChangeReceiver(
        injector: MembersInjector<TimeDateOrTZChangeReceiver>
    ): MembersInjector<*> = injector
}
