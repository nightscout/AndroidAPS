package info.nightscout.pump.combov2.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import info.nightscout.pump.combov2.ComboV2Plugin

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class ComboV2Module {

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1060)
    abstract fun bindComboV2Plugin(plugin: ComboV2Plugin): PluginBase
}
