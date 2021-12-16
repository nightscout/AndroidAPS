package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService
import info.nightscout.androidaps.plugins.general.overview.notifications.DismissNotificationService
import info.nightscout.androidaps.plugins.general.persistentNotification.DummyService
import info.nightscout.androidaps.plugins.general.wear.wearintegration.WatchUpdaterService
import info.nightscout.androidaps.services.AlarmSoundService
import info.nightscout.androidaps.services.LocationService

@Module
@Suppress("unused")
abstract class ServicesModule {

    @ContributesAndroidInjector abstract fun contributesAlarmSoundService(): AlarmSoundService
    @ContributesAndroidInjector abstract fun contributesDismissNotificationService(): DismissNotificationService
    @ContributesAndroidInjector abstract fun contributesDummyService(): DummyService
    @ContributesAndroidInjector abstract fun contributesLocationService(): LocationService
    @ContributesAndroidInjector abstract fun contributesNSClientService(): NSClientService
    @ContributesAndroidInjector abstract fun contributesWatchUpdaterService(): WatchUpdaterService
}