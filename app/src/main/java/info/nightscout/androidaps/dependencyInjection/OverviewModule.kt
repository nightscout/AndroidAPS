package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationWithAction

@Module
@Suppress("unused")
abstract class OverviewModule {
    @ContributesAndroidInjector abstract fun notificationWithActionInjector(): NotificationWithAction

    @ContributesAndroidInjector abstract fun graphDataInjector(): GraphData
}