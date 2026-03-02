package app.aaps.pump.omnipod.eros.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.omnipod.common.di.ActivityScope
import app.aaps.pump.omnipod.common.di.OmnipodWizardModule
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.manager.AapsErosPodStateManager
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager
import app.aaps.pump.omnipod.eros.rileylink.service.RileyLinkOmnipodService
import app.aaps.pump.omnipod.eros.ui.ErosPodHistoryActivity
import app.aaps.pump.omnipod.eros.ui.ErosPodManagementActivity
import app.aaps.pump.omnipod.eros.ui.OmnipodErosOverviewFragment
import app.aaps.pump.omnipod.eros.ui.wizard.activation.ErosPodActivationWizardActivity
import app.aaps.pump.omnipod.eros.ui.wizard.deactivation.ErosPodDeactivationWizardActivity
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

    // ACTIVITIES

    @ContributesAndroidInjector
    abstract fun contributesErosPodManagementActivity(): ErosPodManagementActivity
    @ContributesAndroidInjector abstract fun contributesErosPodHistoryActivity(): ErosPodHistoryActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [OmnipodWizardModule::class, OmnipodErosWizardViewModelsModule::class])
    abstract fun contributesErosActivationWizardActivity(): ErosPodActivationWizardActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [OmnipodWizardModule::class, OmnipodErosWizardViewModelsModule::class])
    abstract fun contributesErosDeactivationWizardActivity(): ErosPodDeactivationWizardActivity

    // FRAGMENTS

    @ContributesAndroidInjector
    abstract fun contributesOmnipodErosOverviewFragment(): OmnipodErosOverviewFragment

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
