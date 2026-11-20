package app.aaps.plugins.eversense.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.plugins.eversense.placement.EversensePlacementActivity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * The Eversense screens that fill their own fields. They land in `AppRootGraph.contributedMemberInjectors`, as the pump
 * drivers' do, so neither `:plugins:source` nor `:app` has to name this module.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object EversenseMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(EversensePlacementActivity::class)
    fun bindEversensePlacementActivity(injector: MembersInjector<EversensePlacementActivity>): MembersInjector<*> = injector
}
