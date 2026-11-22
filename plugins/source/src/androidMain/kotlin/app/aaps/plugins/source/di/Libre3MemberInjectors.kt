package app.aaps.plugins.source.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.plugins.source.activities.Libre3StartActivity
import app.aaps.plugins.source.activities.Libre3StatusActivity
import app.aaps.plugins.source.activities.Libre3WarmupActivity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for the Libre 3 screens. The driver log screen they open is in
 * [CgmMemberInjectors], because every native CGM driver shares it.
 *
 * Android constructs an activity, so it cannot take its dependencies in a constructor.
 * [app.aaps.core.ui.compose.MetroAppCompatActivity] fills the fields from this map.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object Libre3MemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(Libre3StatusActivity::class)
    fun bindLibre3StatusActivity(
        injector: MembersInjector<Libre3StatusActivity>
    ): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(Libre3StartActivity::class)
    fun bindLibre3StartActivity(
        injector: MembersInjector<Libre3StartActivity>
    ): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(Libre3WarmupActivity::class)
    fun bindLibre3WarmupActivity(
        injector: MembersInjector<Libre3WarmupActivity>
    ): MembersInjector<*> = injector
}
