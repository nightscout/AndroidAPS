package app.aaps.ui.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.ui.activities.ErrorActivity
import app.aaps.ui.widget.BgGraphWidget
import app.aaps.ui.widget.CompactBgWidget
import app.aaps.ui.widget.SmallWidget
import app.aaps.ui.widget.Widget
import app.aaps.ui.widget.WidgetConfigureActivity
import app.aaps.ui.widget.glance.WidgetDependencies
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for this module's activities and home screen widgets.
 * Android constructs an activity, so it cannot take its dependencies in a constructor - it fills its own
 * fields once it exists. `MetroAppCompatActivity` does that in `onCreate` from this map, which is the
 * same shape the pump packets use, only reached through the application rather than a passed injector.
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

    // The widgets are AppWidgetProviders, so they cannot use MetroBroadcastReceiver - they already
    // extend a framework base. They call `injectMetroMembers` in onReceive instead, which reaches this
    // same map. Each one needs its own entry: the lookup uses the runtime class.
    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(Widget::class)
    fun bindWidget(injector: MembersInjector<Widget>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BgGraphWidget::class)
    fun bindBgGraphWidget(injector: MembersInjector<BgGraphWidget>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CompactBgWidget::class)
    fun bindCompactBgWidget(injector: MembersInjector<CompactBgWidget>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SmallWidget::class)
    fun bindSmallWidget(injector: MembersInjector<SmallWidget>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(WidgetDependencies::class)
    fun bindWidgetDependencies(injector: MembersInjector<WidgetDependencies>): MembersInjector<*> = injector
}
