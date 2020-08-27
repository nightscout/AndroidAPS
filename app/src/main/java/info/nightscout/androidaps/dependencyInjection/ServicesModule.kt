package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService
import info.nightscout.androidaps.plugins.general.overview.notifications.DismissNotificationService
import info.nightscout.androidaps.plugins.general.persistentNotification.DummyService
import info.nightscout.androidaps.plugins.general.wear.wearintegration.WatchUpdaterService
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkService
import info.nightscout.androidaps.plugins.pump.insight.InsightAlertService
import info.nightscout.androidaps.plugins.pump.insight.connection_service.InsightConnectionService
import info.nightscout.androidaps.plugins.pump.medtronic.service.RileyLinkMedtronicService
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.service.RileyLinkOmnipodService
import info.nightscout.androidaps.services.AlarmSoundService
import info.nightscout.androidaps.services.DataService
import info.nightscout.androidaps.services.LocationService

@Module
@Suppress("unused")
abstract class ServicesModule {

    @ContributesAndroidInjector abstract fun contributesAlarmSoundService(): AlarmSoundService
    @ContributesAndroidInjector abstract fun contributesDataService(): DataService
    @ContributesAndroidInjector abstract fun contributesDismissNotificationService(): DismissNotificationService
    @ContributesAndroidInjector abstract fun contributesDummyService(): DummyService
    @ContributesAndroidInjector abstract fun contributesLocationService(): LocationService
    @ContributesAndroidInjector abstract fun contributesNSClientService(): NSClientService
    @ContributesAndroidInjector abstract fun contributesWatchUpdaterService(): WatchUpdaterService
    @ContributesAndroidInjector abstract fun contributesInsightAlertService(): InsightAlertService
    @ContributesAndroidInjector abstract fun contributesInsightConnectionService(): InsightConnectionService
    @ContributesAndroidInjector abstract fun contributesRileyLinkService(): RileyLinkService
    @ContributesAndroidInjector abstract fun contributesRileyLinkMedtronicService(): RileyLinkMedtronicService
    @ContributesAndroidInjector abstract fun contributesRileyLinkOmnipodService(): RileyLinkOmnipodService
}