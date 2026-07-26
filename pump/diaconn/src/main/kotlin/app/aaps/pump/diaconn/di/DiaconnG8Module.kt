package app.aaps.pump.diaconn.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.diaconn.DiaconnG8Plugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module(
    includes = [
        DiaconnG8ActivitiesModule::class,
        DiaconnG8ServiceModule::class,
        DiaconnG8PacketModule::class,
        DiaconnHistoryModule::class,
        DiaconnLogUploaderModule::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class DiaconnG8Module {

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1100)
    abstract fun bindDiaconnG8Plugin(plugin: DiaconnG8Plugin): PluginBase
}
