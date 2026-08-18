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
 * Dagger wiring for `:pump:virtual`.
 *
 * It lives here rather than in the pump module because a module that runs annotation processing
 * cannot be multiplatform: the AGP multiplatform library target has no Java compilation step, and
 * Dagger emits Java. Keeping the graph in `:app` is what let `:pump:virtual` become a KMP module -
 * the same lift already done for `:core:objects` in [CoreObjectsModule].
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
