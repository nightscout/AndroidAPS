package app.aaps.pump.danar.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danarv2.DanaRv2Plugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module(
    includes = [
        DanaRCommModule::class,
        DanaRServicesModule::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class DanaRModule {

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1010)
    abstract fun bindDanaRPlugin(plugin: DanaRPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1020)
    abstract fun bindDanaRKoreanPlugin(plugin: DanaRKoreanPlugin): PluginBase

    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1030)
    abstract fun bindDanaRv2Plugin(plugin: DanaRv2Plugin): PluginBase
}
