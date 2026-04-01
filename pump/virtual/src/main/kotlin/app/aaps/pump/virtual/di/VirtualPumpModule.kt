package app.aaps.pump.virtual.di

import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.pump.virtual.VirtualPumpPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(
    includes = [
        VirtualPumpModule.Bindings::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class VirtualPumpModule {

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindVirtualPump(virtualPumpPlugin: VirtualPumpPlugin): VirtualPump
    }

}