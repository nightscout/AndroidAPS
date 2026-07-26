package app.aaps.plugins.smoothing.di

import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.smoothing.AvgSmoothingPlugin
import app.aaps.plugins.smoothing.ExponentialSmoothingPlugin
import app.aaps.plugins.smoothing.NoSmoothingPlugin
import app.aaps.plugins.smoothing.UnscentedKalmanFilterPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

/**
 * Self-registration of :plugins:smoothing plugins into the global @AllConfigs plugin map
 * (@IntKey block 600–630, step 10). Including :plugins:smoothing in settings.gradle is enough — no central
 * list edit needed. See PluginsListModule for the overall @IntKey ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class SmoothingPluginsListModule {

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(600)
    abstract fun bindNoSmoothingPlugin(plugin: NoSmoothingPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(610)
    abstract fun bindExponentialSmoothingPlugin(plugin: ExponentialSmoothingPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(620)
    abstract fun bindAvgSmoothingPlugin(plugin: AvgSmoothingPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(630)
    abstract fun bindUnscentedKalmanFilterPlugin(plugin: UnscentedKalmanFilterPlugin): PluginBase
}
