package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService
import info.nightscout.androidaps.services.DataService

@Module
@Suppress("unused")
abstract class ServicesModule {

    @ContributesAndroidInjector abstract fun contributesDataService(): DataService
    @ContributesAndroidInjector abstract fun contributesNSClientService(): NSClientService
}