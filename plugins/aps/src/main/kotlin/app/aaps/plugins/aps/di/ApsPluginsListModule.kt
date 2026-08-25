package app.aaps.plugins.aps.di

import app.aaps.core.interfaces.di.APS
import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.aps.loop.LoopPlugin
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSAutoISF.OpenAPSAutoISFPlugin
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

/**
 * Self-registration of :plugins:aps plugins into the plugin maps (@IntKey block 200–240, step 10).
 * Loop is @APS (loop-enabled builds only); the OpenAPS engines and Autotune are @AllConfigs.
 * Including :plugins:aps in settings.gradle is enough — no central list edit needed.
 * See PluginsListModule for the overall @IntKey ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class ApsPluginsListModule {

    @Binds
    @APS
    @IntoMap
    @IntKey(200)
    abstract fun bindLoopPlugin(plugin: LoopPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(210)
    abstract fun bindOpenAPSAMAPlugin(plugin: OpenAPSAMAPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(220)
    abstract fun bindOpenAPSSMBPlugin(plugin: OpenAPSSMBPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(230)
    abstract fun bindOpenAPSAutoISFPlugin(plugin: OpenAPSAutoISFPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(240)
    abstract fun bindAutotunePlugin(plugin: AutotunePlugin): PluginBase
}
