package app.aaps.di

import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sensitivity.SensitivityAAPSPlugin
import app.aaps.plugins.sensitivity.SensitivityOref1Plugin
import app.aaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Singleton

/**
 * Dagger wiring for `:plugins:sensitivity`, lifted out of the plugin module so it can be
 * multiplatform.
 *
 * One such file per converted module, so that moving off this arrangement later is a per-module move.
 * Why no KMP module may carry a Dagger annotation - and why the mistake passes the build instead of
 * failing it - is in `_docs/KMP_IOS_FEASIBILITY.md`, under "Decisions taken".
 *
 * Self-registration into the global @AllConfigs plugin map keeps its @IntKey block 100-120, step 10.
 * See PluginsListModule for the overall @IntKey ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
class SensitivityPluginsModule {

    @Provides
    @Singleton
    fun provideSensitivityAAPSPlugin(
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        preferences: Preferences,
        dateUtil: DateUtil,
        activePlugin: ActivePlugin
    ): SensitivityAAPSPlugin = SensitivityAAPSPlugin(aapsLogger, rh, preferences, dateUtil, activePlugin)

    @Provides
    @Singleton
    fun provideSensitivityWeightedAveragePlugin(
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        preferences: Preferences,
        dateUtil: DateUtil,
        activePlugin: ActivePlugin
    ): SensitivityWeightedAveragePlugin = SensitivityWeightedAveragePlugin(aapsLogger, rh, preferences, dateUtil, activePlugin)

    @Provides
    @Singleton
    fun provideSensitivityOref1Plugin(
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        preferences: Preferences,
        dateUtil: DateUtil
    ): SensitivityOref1Plugin = SensitivityOref1Plugin(aapsLogger, rh, preferences, dateUtil)

    @Module
    @InstallIn(SingletonComponent::class)
    @Suppress("unused")
    abstract class Bindings {

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
}
