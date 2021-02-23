package info.nightscout.androidaps.plugins.pump.omnipod.dash.dagger

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.dagger.ActivityScope
import info.nightscout.androidaps.plugins.pump.omnipod.common.dagger.OmnipodWizardModule
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.BleManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.DashPodManagementActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.OmnipodDashOverviewFragment
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.DashPodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.deactivation.DashPodDeactivationWizardActivity

@Module
@Suppress("unused")
abstract class OmnipodDashModule {
    // ACTIVITIES

    @ContributesAndroidInjector
    abstract fun contributesDashPodManagementActivity(): DashPodManagementActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [OmnipodWizardModule::class, OmnipodDashWizardViewModelsModule::class])
    abstract fun contributesDashActivationWizardActivity(): DashPodActivationWizardActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [OmnipodWizardModule::class, OmnipodDashWizardViewModelsModule::class])
    abstract fun contributesDashDeactivationWizardActivity(): DashPodDeactivationWizardActivity

    // FRAGMENTS

    @ContributesAndroidInjector
    abstract fun contributesOmnipodDashOverviewFragment(): OmnipodDashOverviewFragment

    @ContributesAndroidInjector
    abstract fun contributesBleManager(): BleManager
}
