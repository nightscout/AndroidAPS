package app.aaps.pump.insight.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.pump.insight.InsightAlertService
import app.aaps.pump.insight.app_layer.activities.InsightAlertActivity
import app.aaps.pump.insight.connection_service.InsightConnectionService
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
 * The activity uses `MetroAppCompatActivity` from `:core:ui`. The two services cannot use `MetroService`,
 * which lives in `:core:objects` and is not a dependency here, so they call `injectMetroMembers`
 * themselves - the same call that base class makes.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object InsightMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(InsightAlertActivity::class)
    fun bindInsightAlertActivity(injector: MembersInjector<InsightAlertActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(InsightAlertService::class)
    fun bindInsightAlertService(injector: MembersInjector<InsightAlertService>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(InsightConnectionService::class)
    fun bindInsightConnectionService(injector: MembersInjector<InsightConnectionService>): MembersInjector<*> = injector
}
