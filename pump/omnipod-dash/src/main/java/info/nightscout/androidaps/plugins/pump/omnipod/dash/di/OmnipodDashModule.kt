package info.nightscout.androidaps.plugins.pump.omnipod.dash.di

import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.di.ActivityScope
import info.nightscout.androidaps.plugins.pump.omnipod.common.di.OmnipodWizardModule
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.OmnipodDashManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.OmnipodDashManagerImpl
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashBleManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashBleManagerImpl
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManagerImpl
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.DashPodHistoryActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.DashPodManagementActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.OmnipodDashOverviewFragment
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.DashPodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.deactivation.DashPodDeactivationWizardActivity

@Module(includes = [OmnipodDashHistoryModule::class])
@Suppress("unused")
abstract class OmnipodDashModule {
    // ACTIVITIES

    @ContributesAndroidInjector
    abstract fun contributesDashPodHistoryActivity(): DashPodHistoryActivity

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

    // MANAGERS

    @Binds
    abstract fun bindsOmnipodDashBleManagerImpl(bleManager: OmnipodDashBleManagerImpl): OmnipodDashBleManager

    @Binds
    abstract fun bindsOmnipodDashPodStateManagerImpl(podStateManager: OmnipodDashPodStateManagerImpl): OmnipodDashPodStateManager

    @Binds
    abstract fun bindsOmnipodDashManagerImpl(omnipodManager: OmnipodDashManagerImpl): OmnipodDashManager
}
