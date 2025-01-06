package app.aaps.plugins.main.di

import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.plugins.main.general.overview.OverviewDataImpl
import app.aaps.plugins.main.general.overview.OverviewFragment
import app.aaps.plugins.main.general.overview.OverviewMenusImpl
import app.aaps.plugins.main.general.overview.graphData.GraphData
import app.aaps.plugins.main.general.overview.notifications.NotificationWithAction
import app.aaps.plugins.main.general.overview.notifications.receivers.DismissNotificationReceiver
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module(
    includes = [
        OverviewModule.Bindings::class
    ]
)
@Suppress("unused")
abstract class OverviewModule {

    @ContributesAndroidInjector abstract fun contributesDismissNotificationReceiver(): DismissNotificationReceiver
    @ContributesAndroidInjector abstract fun contributesOverviewFragment(): OverviewFragment
    @ContributesAndroidInjector abstract fun notificationWithActionInjector(): NotificationWithAction
    @ContributesAndroidInjector abstract fun graphDataInjector(): GraphData

    @Module
    interface Bindings {

        @Binds fun bindOverviewMenus(overviewMenusImpl: OverviewMenusImpl): OverviewMenus
        @Binds fun bindOverviewData(overviewData: OverviewDataImpl): OverviewData
    }
}