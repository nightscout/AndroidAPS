package app.aaps.plugins.calibration.di

import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.calibration.LinearCalibrationPlugin
import app.aaps.plugins.calibration.NoCalibrationPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

/**
 * Self-registration of :plugins:calibration plugins into the global @AllConfigs plugin map
 * (@IntKey block 700–710, step 10). Including :plugins:calibration in settings.gradle is enough — no central
 * list edit needed. See PluginsListModule for the overall @IntKey ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class CalibrationPluginsListModule {

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(700)
    abstract fun bindNoCalibrationPlugin(plugin: NoCalibrationPlugin): PluginBase

    @Binds
    @AllConfigs
    @IntoMap
    @IntKey(710)
    abstract fun bindLinearCalibrationPlugin(plugin: LinearCalibrationPlugin): PluginBase
}
