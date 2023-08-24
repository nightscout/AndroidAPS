package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.general.wear.activities.CwfInfosActivity
import info.nightscout.plugins.general.wear.wearintegration.DataLayerListenerServiceMobile

@Module
@Suppress("unused")
abstract class WearModule {

    @ContributesAndroidInjector abstract fun contributesWatchUpdaterService(): DataLayerListenerServiceMobile
    @ContributesAndroidInjector abstract fun contributesCustomWatchfaceInfosActivity(): CwfInfosActivity
}