package app.aaps.plugins.source.di

import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.NSClientSourcePlugin
import app.aaps.plugins.source.XdripSourcePlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

/**
 * Self-registration of :plugins:source BG-source plugins into the global @AllConfigs plugin map
 * (@IntKey block 400–550, step 10). Including :plugins:source in settings.gradle is enough — no central
 * list edit needed. See PluginsListModule for the overall @IntKey ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class SourcePluginsListModule {

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(400)
    abstract fun bindXdripSourcePlugin(plugin: XdripSourcePlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(410)
    abstract fun bindNSClientSourcePlugin(plugin: NSClientSourcePlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(440)
    abstract fun bindDexcomPlugin(plugin: DexcomPlugin): PluginBase

}
