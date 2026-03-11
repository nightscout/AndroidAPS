package app.aaps.pump.equil.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.equil.EquilPumpPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module(
    includes = [
        EquilServicesModule::class,
        EquilHistoryModule::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class EquilModule {

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1130)
    abstract fun bindEquilPumpPlugin(plugin: EquilPumpPlugin): PluginBase
}
