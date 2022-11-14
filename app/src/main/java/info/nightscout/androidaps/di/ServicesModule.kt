package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.persistentNotification.DummyService
import info.nightscout.androidaps.plugins.general.wear.wearintegration.DataLayerListenerServiceMobile
import info.nightscout.androidaps.services.AlarmSoundService
import info.nightscout.automation.services.LocationService
import info.nightscout.plugins.general.overview.notifications.DismissNotificationService

@Module
@Suppress("unused")
abstract class ServicesModule {

    @ContributesAndroidInjector abstract fun contributesAlarmSoundService(): AlarmSoundService
    @ContributesAndroidInjector abstract fun contributesDismissNotificationService(): DismissNotificationService
    @ContributesAndroidInjector abstract fun contributesDummyService(): DummyService
    @ContributesAndroidInjector abstract fun contributesLocationService(): LocationService
    @ContributesAndroidInjector abstract fun contributesWatchUpdaterService(): DataLayerListenerServiceMobile
}