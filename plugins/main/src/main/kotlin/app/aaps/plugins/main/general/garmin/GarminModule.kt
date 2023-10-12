package app.aaps.plugins.main.general.garmin

import dagger.Binds
import dagger.Module

@Module
abstract class GarminModule {
    @Suppress("unused")
    @Binds abstract fun bindLoopHub(loopHub: LoopHubImpl): LoopHub
}