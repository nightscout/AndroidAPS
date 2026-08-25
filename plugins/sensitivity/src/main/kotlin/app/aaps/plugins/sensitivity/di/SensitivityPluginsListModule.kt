package app.aaps.plugins.sensitivity.di

import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.sensitivity.SensitivityAAPSPlugin
import app.aaps.plugins.sensitivity.SensitivityOref1Plugin
import app.aaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

/**
 * Self-registration of :plugins:sensitivity plugins into the global @AllConfigs plugin map
 * (@IntKey block 100–120, step 10). Including :plugins:sensitivity in settings.gradle is enough —
 * no central list edit needed. See PluginsListModule for the overall @IntKey ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class SensitivityPluginsListModule {

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(100)
    abstract fun bindSensitivityAAPSPlugin(plugin: SensitivityAAPSPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(110)
    abstract fun bindSensitivityWeightedAveragePlugin(plugin: SensitivityWeightedAveragePlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(120)
    abstract fun bindSensitivityOref1Plugin(plugin: SensitivityOref1Plugin): PluginBase
}
