package app.aaps.pump.omnipod.eros.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.manager.AapsErosPodStateManager
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager
import app.aaps.pump.omnipod.eros.rileylink.service.RileyLinkOmnipodService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module(includes = [OmnipodErosHistoryModule::class])
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class OmnipodErosModule {

    // SERVICES

    @ContributesAndroidInjector
    abstract fun contributesOmnipodRileyLinkCommunicationManagerProvider(): OmnipodRileyLinkCommunicationManager

    @ContributesAndroidInjector
    abstract fun contributesRileyLinkOmnipodService(): RileyLinkOmnipodService

    companion object {

        @Provides
        fun erosPodStateManagerProvider(aapsErosPodStateManager: AapsErosPodStateManager): ErosPodStateManager = aapsErosPodStateManager
    }

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1070)
    abstract fun bindOmnipodErosPumpPlugin(plugin: OmnipodErosPumpPlugin): PluginBase
}
