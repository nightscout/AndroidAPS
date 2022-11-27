package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.general.overview.OverviewFragment
import info.nightscout.plugins.general.overview.activities.QuickWizardListActivity
import info.nightscout.plugins.general.overview.dialogs.EditQuickWizardDialog
import info.nightscout.plugins.general.overview.graphData.GraphData
import info.nightscout.plugins.general.overview.notifications.DismissNotificationService
import info.nightscout.plugins.general.overview.notifications.NotificationWithAction

@Module
@Suppress("unused")
abstract class OverviewModule {

    @ContributesAndroidInjector abstract fun contributesDismissNotificationService(): DismissNotificationService
    @ContributesAndroidInjector abstract fun contributesQuickWizardListActivity(): QuickWizardListActivity
    @ContributesAndroidInjector abstract fun contributesEditQuickWizardDialog(): EditQuickWizardDialog
    @ContributesAndroidInjector abstract fun contributesOverviewFragment(): OverviewFragment
    @ContributesAndroidInjector abstract fun notificationWithActionInjector(): NotificationWithAction
    @ContributesAndroidInjector abstract fun graphDataInjector(): GraphData
}