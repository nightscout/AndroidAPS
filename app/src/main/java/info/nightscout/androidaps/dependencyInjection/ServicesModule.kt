package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.services.DataService

@Module
@Suppress("unused")
abstract class ServicesModule {

    @ContributesAndroidInjector abstract fun contributesDataService(): DataService
}