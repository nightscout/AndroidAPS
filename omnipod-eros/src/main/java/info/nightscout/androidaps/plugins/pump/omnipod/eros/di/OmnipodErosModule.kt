package info.nightscout.androidaps.plugins.pump.omnipod.eros.di

import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.di.ActivityScope
import info.nightscout.androidaps.plugins.pump.omnipod.common.di.OmnipodWizardModule
import info.nightscout.androidaps.plugins.pump.omnipod.eros.data.RLHistoryItemOmnipod
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.ErosPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsErosPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.service.RileyLinkOmnipodService
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.ErosPodHistoryActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.ErosPodManagementActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.OmnipodErosOverviewFragment
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.ErosPodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.ErosPodDeactivationWizardActivity

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

    // DATA

    @ContributesAndroidInjector abstract fun contributesRlHistoryItemOmnipod(): RLHistoryItemOmnipod

    companion object {

        @Provides
        fun erosPodStateManagerProvider(aapsErosPodStateManager: AapsErosPodStateManager): ErosPodStateManager = aapsErosPodStateManager
    }
}
