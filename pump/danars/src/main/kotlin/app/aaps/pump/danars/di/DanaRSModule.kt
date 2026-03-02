package app.aaps.pump.danars.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.comm.DanaRSPacket
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module(
    includes = [
        DanaRSCommModule::class,
        DanaRSActivitiesModule::class,
        DanaRSServicesModule::class
    ]
)
@Suppress("unused")
abstract class DanaRSModule {

    companion object {

        @Provides
        fun providesCommands(
            @DanaRSCommModule.DanaRSCommand rsCommands: Set<@JvmSuppressWildcards DanaRSPacket>,
        ): Set<@JvmSuppressWildcards DanaRSPacket> = rsCommands
    }

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1040)
    abstract fun bindDanaRSPlugin(plugin: DanaRSPlugin): PluginBase
}
