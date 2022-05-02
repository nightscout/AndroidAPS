package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ProfileStore
import info.nightscout.androidaps.plugins.aps.loop.APSResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData

@Module
@Suppress("unused")
abstract class CoreDataClassesModule {

    @ContributesAndroidInjector abstract fun pumpEnactResultInjector(): PumpEnactResult
    @ContributesAndroidInjector abstract fun apsResultInjector(): APSResult
    @ContributesAndroidInjector abstract fun autosensDataInjector(): AutosensData

    @ContributesAndroidInjector abstract fun profileStoreInjector(): ProfileStore
}
