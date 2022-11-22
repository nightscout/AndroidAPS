package info.nightscout.plugins.aps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.aps.OpenAPSFragment
import info.nightscout.plugins.di.AutotuneModule

@Module(
    includes = [
        AutotuneModule::class,
        AlgModule::class
    ]
)

@Suppress("unused")
abstract class ApsModule {
    @ContributesAndroidInjector abstract fun contributesOpenAPSFragment(): OpenAPSFragment
}