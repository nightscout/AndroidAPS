package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.PodHistoryActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.PodManagementActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod.InitActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod.InitPodTask
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages.InitPodRefreshAction
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages.PodInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.removepod.RemoveActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsPodStateManager

@Module
@Suppress("unused")
abstract class OmnipodModule {

    // Activities
    @ContributesAndroidInjector
    abstract fun contributesPodManagementActivity(): PodManagementActivity
    @ContributesAndroidInjector abstract fun contributesPodHistoryActivity(): PodHistoryActivity

    // Fragments
    @ContributesAndroidInjector abstract fun initActionFragment(): InitActionFragment
    @ContributesAndroidInjector abstract fun removeActionFragment(): RemoveActionFragment
    @ContributesAndroidInjector abstract fun podInfoFragment(): PodInfoFragment

    // Service
    @ContributesAndroidInjector
    abstract fun omnipodCommunicationManagerProvider(): OmnipodCommunicationManager
    @ContributesAndroidInjector abstract fun aapsOmnipodManagerProvider(): AapsOmnipodManager

    // Data
    @ContributesAndroidInjector abstract fun initPodRefreshAction(): InitPodRefreshAction
    @ContributesAndroidInjector abstract fun podStateManager(): PodStateManager
    @ContributesAndroidInjector abstract fun initPodTask(): InitPodTask
    @ContributesAndroidInjector abstract fun omnipodPumpPlugin(): OmnipodPumpPlugin

    companion object {
        @Provides
        fun podStateManagerProvider(aapsPodStateManager: AapsPodStateManager): PodStateManager = aapsPodStateManager
    }
}
