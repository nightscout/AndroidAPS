package app.aaps.di.metro

import app.aaps.ComposeMainActivity
import app.aaps.core.interfaces.di.FeatureMemberInjectors
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for the activities this module owns.
 *
 * Same shape as `UiMemberInjectors` in `:ui`: Android builds the activity, so it cannot take its
 * dependencies in a constructor and fills its own fields once it exists. `MetroAppCompatActivity`
 * reads this map in `onCreate`.
 *
 * An activity missing from here fails on launch with its own name in the message, rather than later
 * at the first use of a field that was never set.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AppMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ComposeMainActivity::class)
    fun bindComposeMainActivity(injector: MembersInjector<ComposeMainActivity>): MembersInjector<*> = injector
}
