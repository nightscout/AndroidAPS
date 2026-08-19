package app.aaps.di

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.calibration.LinearCalibrationPlugin
import app.aaps.plugins.calibration.NoCalibrationPlugin
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Singleton

/**
 * Dagger wiring for `:plugins:calibration`.
 *
 * Lifted out of the plugin module so it can be multiplatform - annotation processing and the AGP
 * multiplatform library target are mutually exclusive. Same as [CoreObjectsModule],
 * [VirtualPumpModule] and [SmoothingPluginsModule].
 *
 * Registration keeps its @IntKey block 700-710, step 10.
 */
@Module
@InstallIn(SingletonComponent::class)
class CalibrationPluginsModule {

    @Provides
    @Singleton
    fun provideNoCalibrationPlugin(aapsLogger: AAPSLogger, rh: ResourceHelper): NoCalibrationPlugin =
        NoCalibrationPlugin(aapsLogger, rh)

    @Provides
    @Singleton
    fun provideLinearCalibrationPlugin(
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        dateUtil: DateUtil,
        persistenceLayer: PersistenceLayer,
        notificationManager: NotificationManager,
        glucoseStatusProvider: GlucoseStatusProvider,
        rxBus: RxBus,
        profileUtil: ProfileUtil
    ): LinearCalibrationPlugin = LinearCalibrationPlugin(
        aapsLogger, rh, dateUtil, persistenceLayer, notificationManager, glucoseStatusProvider, rxBus, profileUtil
    )

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class Bindings {

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
}
