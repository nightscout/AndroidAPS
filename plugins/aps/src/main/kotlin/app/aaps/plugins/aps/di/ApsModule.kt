package app.aaps.plugins.aps.di

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.aps.loop.LoopPlugin
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(
    includes = [
        AutotuneModule::class,
        LoopModule::class,
        ApsModule.Bindings::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class ApsModule {

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindLoop(loopPlugin: LoopPlugin): Loop
        @Binds fun bindAutotune(autotunePlugin: AutotunePlugin): Autotune
    }
}