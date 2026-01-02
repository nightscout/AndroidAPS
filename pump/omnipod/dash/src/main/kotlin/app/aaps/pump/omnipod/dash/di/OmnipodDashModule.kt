package app.aaps.pump.omnipod.dash.di

import app.aaps.pump.omnipod.common.di.ActivityScope
import app.aaps.pump.omnipod.common.di.OmnipodWizardModule
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManagerImpl
import app.aaps.pump.omnipod.dash.driver.comm.OmnipodDashBleManager
import app.aaps.pump.omnipod.dash.driver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManagerImpl
import app.aaps.pump.omnipod.dash.ui.DashPodHistoryActivity
import app.aaps.pump.omnipod.dash.ui.DashPodManagementActivity
import app.aaps.pump.omnipod.dash.ui.OmnipodDashOverviewFragment
import app.aaps.pump.omnipod.dash.ui.wizard.activation.DashPodActivationWizardActivity
import app.aaps.pump.omnipod.dash.ui.wizard.deactivation.DashPodDeactivationWizardActivity
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

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
