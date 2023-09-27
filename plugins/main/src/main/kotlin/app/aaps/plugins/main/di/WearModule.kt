package app.aaps.plugins.main.di

import app.aaps.plugins.main.general.wear.activities.CwfInfosActivity
import app.aaps.plugins.main.general.wear.wearintegration.DataLayerListenerServiceMobile
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class WearModule {

    @ContributesAndroidInjector abstract fun contributesWatchUpdaterService(): DataLayerListenerServiceMobile
    @ContributesAndroidInjector abstract fun contributesCustomWatchfaceInfosActivity(): CwfInfosActivity
}