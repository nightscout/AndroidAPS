package info.nightscout.plugins.aps.di

import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.interfaces.autotune.Autotune
import info.nightscout.plugins.aps.OpenAPSFragment
import info.nightscout.plugins.general.autotune.AutotunePlugin

@Module(
    includes = [
        AutotuneModule::class,
        AlgModule::class,

        ApsModule.Bindings::class
    ]
)

@Suppress("unused")
abstract class ApsModule {

    @ContributesAndroidInjector abstract fun contributesOpenAPSFragment(): OpenAPSFragment

    @Module
    interface Bindings {

        @Binds fun bindAutotuneInterface(autotunePlugin: AutotunePlugin): Autotune
    }
}