package app.aaps.pump.omnipod.eros.di

import app.aaps.pump.omnipod.common.di.ActivityScope
import app.aaps.pump.omnipod.common.di.OmnipodWizardModule
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.manager.AapsErosPodStateManager
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager
import app.aaps.pump.omnipod.eros.rileylink.service.RileyLinkOmnipodService
import app.aaps.pump.omnipod.eros.ui.ErosPodHistoryActivity
import app.aaps.pump.omnipod.eros.ui.ErosPodManagementActivity
import app.aaps.pump.omnipod.eros.ui.OmnipodErosOverviewFragment
import app.aaps.pump.omnipod.eros.ui.wizard.activation.ErosPodActivationWizardActivity
import app.aaps.pump.omnipod.eros.ui.wizard.deactivation.ErosPodDeactivationWizardActivity
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector

@Module(includes = [OmnipodErosHistoryModule::class])
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
}
