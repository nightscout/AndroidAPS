package app.aaps.ui.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.ui.activities.ErrorActivity
import app.aaps.ui.widget.WidgetConfigureActivity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for this module's activities.
 *
 * Android constructs an activity, so it cannot take its dependencies in a constructor - it fills its own
 * fields once it exists. `MetroAppCompatActivity` does that in `onCreate` from this map, which is the
 * same shape the pump packets use, only reached through the application rather than a passed injector.
 *
 * An activity missing from here fails on launch with its own name, where `dagger.android` left the
 * fields unset and failed later at the first use.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object UiMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ErrorActivity::class)
    fun bindErrorActivity(injector: MembersInjector<ErrorActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(WidgetConfigureActivity::class)
    fun bindWidgetConfigureActivity(injector: MembersInjector<WidgetConfigureActivity>): MembersInjector<*> = injector
}
