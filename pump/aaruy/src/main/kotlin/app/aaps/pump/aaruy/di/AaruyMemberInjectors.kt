package app.aaps.pump.aaruy.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.pump.aaruy.services.AaruyService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for the Diaconn protocol packets - the `@ContributesAndroidInjector` replacement.
 * The entries land in `AppRootGraph.contributedMemberInjectors` through `@ContributesTo`. That matters:
 * a graph extension would have to be named by `MetroGraphs`, which is compiled for follower builds where
 * this module does not exist. Contributing into the root instead needs no mention anywhere in `:app`.
 * One line per packet, the same as the `@ContributesAndroidInjector` line it replaces. If a packet is
 * missing, `DiaconnG8Packet`'s init throws by name rather than leaving fields null.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AaruyMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AaruyService::class)
    fun bindAaruyService(injector: MembersInjector<AaruyService>): MembersInjector<*> = injector
}
