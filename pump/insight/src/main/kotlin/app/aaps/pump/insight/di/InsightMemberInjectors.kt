package app.aaps.pump.insight.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.pump.insight.app_layer.activities.InsightAlertActivity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for this module's Android entry points.
 *
 * Only the activity so far. The two services still use `dagger.android`, because `MetroService` lives in
 * `:core:objects` and this module does not depend on it - the activity base is in `:core:ui`, which it
 * does depend on.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object InsightMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(InsightAlertActivity::class)
    fun bindInsightAlertActivity(injector: MembersInjector<InsightAlertActivity>): MembersInjector<*> = injector
}
