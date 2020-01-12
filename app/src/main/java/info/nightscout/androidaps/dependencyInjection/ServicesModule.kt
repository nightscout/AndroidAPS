package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService
import info.nightscout.androidaps.plugins.general.persistentNotification.DummyService
import info.nightscout.androidaps.plugins.pump.danaR.services.DanaRExecutionService
import info.nightscout.androidaps.plugins.pump.danaRKorean.services.DanaRKoreanExecutionService
import info.nightscout.androidaps.plugins.pump.danaRS.services.DanaRSService
import info.nightscout.androidaps.plugins.pump.danaRv2.services.DanaRv2ExecutionService
import info.nightscout.androidaps.services.AlarmSoundService
import info.nightscout.androidaps.services.DataService
import info.nightscout.androidaps.services.LocationService

@Module
@Suppress("unused")
abstract class ServicesModule {

    @ContributesAndroidInjector abstract fun contributesAlarmSoundService(): AlarmSoundService
    @ContributesAndroidInjector abstract fun contributesDataService(): DataService
    @ContributesAndroidInjector abstract fun contributesDummyService(): DummyService
    @ContributesAndroidInjector abstract fun contributesLocationService(): LocationService
    @ContributesAndroidInjector abstract fun contributesNSClientService(): NSClientService
    @ContributesAndroidInjector abstract fun contributesDanaRSService(): DanaRSService
    @ContributesAndroidInjector abstract fun contributesDanaRv2ExecutionService(): DanaRv2ExecutionService
    @ContributesAndroidInjector abstract fun contributesDanaRExecutionService(): DanaRExecutionService
    @ContributesAndroidInjector abstract fun contributesDanaRKoreanExecutionService(): DanaRKoreanExecutionService
}