package app.aaps.pump.virtual.di

import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.pump.virtual.VirtualPumpPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module(
    includes = [
        VirtualPumpModule.Bindings::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class VirtualPumpModule {

    // VirtualPump self-registers as @AllConfigs (present in every build config), @IntKey(1000).
    // Real pump drivers use the @PumpDriver map at @IntKey 1010+.
    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(1000)
    abstract fun bindVirtualPumpPlugin(plugin: VirtualPumpPlugin): PluginBase

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindVirtualPump(virtualPumpPlugin: VirtualPumpPlugin): VirtualPump
    }

}