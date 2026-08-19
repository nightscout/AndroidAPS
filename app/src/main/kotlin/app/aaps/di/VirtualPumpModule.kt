package app.aaps.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.virtual.VirtualPumpPlugin
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import kotlinx.coroutines.CoroutineScope
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Dagger wiring for `:pump:virtual`, lifted out of the pump module so it can be multiplatform.
 *
 * One such file per converted module, so that moving off this arrangement later is a per-module move.
 * Why no KMP module may carry a Dagger annotation - and why the mistake passes the build instead of
 * failing it - is in `_docs/KMP_IOS_FEASIBILITY.md`, under "Decisions taken".
 */
@Module
@InstallIn(SingletonComponent::class)
class VirtualPumpModule {

    @Provides
    @Singleton
    fun provideVirtualPumpPlugin(
        aapsLogger: AAPSLogger,
        rxBus: RxBus,
        rh: ResourceHelper,
        preferences: Preferences,
        commandQueue: CommandQueue,
        pumpSync: PumpSync,
        config: Config,
        dateUtil: DateUtil,
        persistenceLayer: PersistenceLayer,
        // A plain factory rather than javax.inject.Provider: that type is JVM only and would pin the
        // plugin to one platform. Dagger still supplies it, from this side.
        pumpEnactResult: Provider<PumpEnactResult>,
        ch: ConcentrationHelper,
        profileFunction: ProfileFunction,
        bolusProgressData: BolusProgressData,
        @ApplicationScope appScope: CoroutineScope
    ): VirtualPumpPlugin = VirtualPumpPlugin(
        aapsLogger = aapsLogger,
        rxBus = rxBus,
        rh = rh,
        preferences = preferences,
        commandQueue = commandQueue,
        pumpSync = pumpSync,
        config = config,
        dateUtil = dateUtil,
        persistenceLayer = persistenceLayer,
        pumpEnactResultProvider = { pumpEnactResult.get() },
        ch = ch,
        profileFunction = profileFunction,
        bolusProgressData = bolusProgressData,
        appScope = appScope
    )

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class Bindings {

        // VirtualPump self-registers as @AllConfigs (present in every build config), @IntKey(1000).
        // Real pump drivers use the @PumpDriver map at @IntKey 1010+.
        @Binds
        @AllConfigs
        @IntoMap
        @IntKey(1000)
        abstract fun bindVirtualPumpPlugin(plugin: VirtualPumpPlugin): PluginBase

        @Binds abstract fun bindVirtualPump(virtualPumpPlugin: VirtualPumpPlugin): VirtualPump
    }
}
