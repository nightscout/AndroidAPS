package info.nightscout.androidaps.plugins.pump.omnipod.eros.dagger

import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.eros.data.RLHistoryItemOmnipod
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.PodHistoryActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.PodManagementActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.PodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.PodDeactivationWizardActivity

@Module
@Suppress("unused")
abstract class OmnipodErosModule {

    // Activities
    @ContributesAndroidInjector
    abstract fun contributesPodManagementActivity(): PodManagementActivity
    @ContributesAndroidInjector abstract fun contributesPodHistoryActivity(): PodHistoryActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [OmnipodWizardModule::class])
    abstract fun contributesActivationWizardActivity(): PodActivationWizardActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [OmnipodWizardModule::class])
    abstract fun contributesDeactivationWizardActivity(): PodDeactivationWizardActivity

    // Service
    @ContributesAndroidInjector
    abstract fun omnipodCommunicationManagerProvider(): OmnipodRileyLinkCommunicationManager

    // Data
    @ContributesAndroidInjector abstract fun rlHistoryItemOmnipod(): RLHistoryItemOmnipod

    companion object {

        @Provides
        fun podStateManagerProvider(aapsPodStateManager: AapsPodStateManager): PodStateManager = aapsPodStateManager
    }
}
