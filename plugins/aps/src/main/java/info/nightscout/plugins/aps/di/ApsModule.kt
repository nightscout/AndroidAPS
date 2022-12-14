package info.nightscout.plugins.aps.di

import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.interfaces.aps.APSResult
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.autotune.Autotune
import info.nightscout.plugins.aps.APSResultObject
import info.nightscout.plugins.aps.OpenAPSFragment
import info.nightscout.plugins.aps.loop.LoopPlugin
import info.nightscout.plugins.general.autotune.AutotunePlugin

@Module(
    includes = [
        AutotuneModule::class,
        AlgModule::class,
        LoopModule::class,
        ApsModule.Bindings::class
    ]
)

@Suppress("unused")
abstract class ApsModule {

    @ContributesAndroidInjector abstract fun contributesOpenAPSFragment(): OpenAPSFragment
    @ContributesAndroidInjector abstract fun apsResultInjector(): APSResultObject

    @Module
    interface Bindings {

        @Binds fun bindLoop(loopPlugin: LoopPlugin): Loop
        @Binds fun bindAutotune(autotunePlugin: AutotunePlugin): Autotune
    }
}