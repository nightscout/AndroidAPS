package app.aaps.plugins.source.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.plugins.source.activities.CgmDriverLogActivity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for the screens that every native CGM driver shares, today the driver log screen.
 *
 * The screens of one driver have their own binding container. This one holds only the shared screens,
 * so each driver can use them without adding a binding twice.
 *
 * Android constructs an activity, so it cannot take its dependencies in a constructor.
 * [app.aaps.core.ui.compose.MetroAppCompatActivity] fills the fields from this map.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object CgmMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CgmDriverLogActivity::class)
    fun bindCgmDriverLogActivity(
        injector: MembersInjector<CgmDriverLogActivity>
    ): MembersInjector<*> = injector
}
