package app.aaps.di

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Dagger wiring for `:plugins:main`, lifted out of the plugin module so it can be multiplatform.
 *
 * One such file per converted module, so that moving off this arrangement later is a per-module move.
 * Why no KMP module may carry a Dagger annotation - and why the mistake passes the build instead of
 * failing it - is in `_docs/KMP_IOS_FEASIBILITY.md`, under "Decisions taken".
 *
 * Self-registration into the global @AllConfigs plugin map keeps its @IntKey 10. Note that @IntKey 0,
 * the persistent notification, is in PersistentNotificationModule - those classes moved to :app
 * rather than becoming multiplatform. See PluginsListModule for the overall ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
class MainPluginsModule {

    @Provides
    @Singleton
    fun provideIobCobCalculatorPlugin(
        aapsLogger: AAPSLogger,
        rxBus: RxBus,
        preferences: Preferences,
        rh: ResourceHelper,
        profileFunction: ProfileFunction,
        activePlugin: ActivePlugin,
        dateUtil: DateUtil,
        persistenceLayer: PersistenceLayer,
        overviewData: OverviewData,
        calculationWorkflow: CalculationWorkflow,
        decimalFormatter: DecimalFormatter,
        processedTbrEbData: ProcessedTbrEbData,
        signals: CalculationSignalsEmitter,
        // Dagger's Provider stays on this side of the border: the plugin takes a plain lambda,
        // because javax.inject is JVM only. Deferring the lookup is what breaks the
        // IobCobCalculator <-> OverviewDataCache construction cycle.
        cache: Provider<OverviewDataCache>
    ): IobCobCalculatorPlugin = IobCobCalculatorPlugin(
        aapsLogger, rxBus, preferences, rh, profileFunction, activePlugin, dateUtil, persistenceLayer,
        overviewData, calculationWorkflow, decimalFormatter, processedTbrEbData, signals
    ) { cache.get() }

    @Module
    @InstallIn(SingletonComponent::class)
    @Suppress("unused")
    abstract class Bindings {

        @Binds abstract fun bindIobCobCalculator(plugin: IobCobCalculatorPlugin): IobCobCalculator

        @Binds
        @AllConfigs
        @IntoMap
        @IntKey(10)
        abstract fun bindIobCobCalculatorPlugin(plugin: IobCobCalculatorPlugin): PluginBase
    }
}
