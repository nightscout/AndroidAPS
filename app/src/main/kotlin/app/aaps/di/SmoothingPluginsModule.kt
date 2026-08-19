package app.aaps.di

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.smoothing.AvgSmoothingPlugin
import app.aaps.plugins.smoothing.ExponentialSmoothingPlugin
import app.aaps.plugins.smoothing.NoSmoothingPlugin
import app.aaps.plugins.smoothing.UnscentedKalmanFilterPlugin
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Singleton

/**
 * Dagger wiring for `:plugins:smoothing`, lifted out of the plugin module so it can be multiplatform.
 *
 * One such file per converted module, so that moving off this arrangement later is a per-module move.
 * Why no KMP module may carry a Dagger annotation - and why the mistake passes the build instead of
 * failing it - is in `_docs/KMP_IOS_FEASIBILITY.md`, under "Decisions taken".
 *
 * Self-registration into the global @AllConfigs plugin map keeps its @IntKey block 600-630, step 10.
 * See PluginsListModule for the overall @IntKey ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
class SmoothingPluginsModule {

    @Provides
    @Singleton
    fun provideNoSmoothingPlugin(aapsLogger: AAPSLogger, rh: ResourceHelper): NoSmoothingPlugin =
        NoSmoothingPlugin(aapsLogger, rh)

    @Provides
    @Singleton
    fun provideExponentialSmoothingPlugin(aapsLogger: AAPSLogger, rh: ResourceHelper): ExponentialSmoothingPlugin =
        ExponentialSmoothingPlugin(aapsLogger, rh)

    @Provides
    @Singleton
    fun provideAvgSmoothingPlugin(aapsLogger: AAPSLogger, rh: ResourceHelper): AvgSmoothingPlugin =
        AvgSmoothingPlugin(aapsLogger, rh)

    @Provides
    @Singleton
    fun provideUnscentedKalmanFilterPlugin(
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        preferences: Preferences,
        persistenceLayer: PersistenceLayer
    ): UnscentedKalmanFilterPlugin = UnscentedKalmanFilterPlugin(aapsLogger, rh, preferences, persistenceLayer)

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class Bindings {

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
}
