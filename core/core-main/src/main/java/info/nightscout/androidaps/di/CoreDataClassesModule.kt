package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResultObject
import info.nightscout.androidaps.plugins.aps.loop.APSResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData
import info.nightscout.core.profile.ProfileStoreObject

@Module
@Suppress("unused")
abstract class CoreDataClassesModule {

    @ContributesAndroidInjector abstract fun pumpEnactResultInjector(): PumpEnactResultObject
    @ContributesAndroidInjector abstract fun apsResultInjector(): APSResult
    @ContributesAndroidInjector abstract fun autosensDataInjector(): AutosensData

    @ContributesAndroidInjector abstract fun profileStoreInjector(): ProfileStoreObject
}
