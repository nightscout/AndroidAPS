package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.PodHistoryActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.PodManagementActivity
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod.InitActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod.InitPodTask
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages.InitPodRefreshAction
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages.PodInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.removepod.RemoveActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.driver.ui.OmnipodUITask

@Module
@Suppress("unused")
abstract class OmnipodModule {

    // Activities
    @ContributesAndroidInjector abstract fun contributesPodManagementActivity(): PodManagementActivity
    @ContributesAndroidInjector abstract fun contributesPodHistoryActivity(): PodHistoryActivity

    // Fragments
    @ContributesAndroidInjector abstract fun initActionFragment() : InitActionFragment
    @ContributesAndroidInjector abstract fun removeActionFragment() : RemoveActionFragment
    @ContributesAndroidInjector abstract fun podInfoFragment() : PodInfoFragment

    // Service
    @ContributesAndroidInjector abstract fun omnipodCommunicationManagerProvider(): OmnipodCommunicationManager
    @ContributesAndroidInjector abstract fun aapsOmnipodManagerProvider(): AapsOmnipodManager

    // Data
    @ContributesAndroidInjector abstract fun omnipodUITaskProvider(): OmnipodUITask
    @ContributesAndroidInjector abstract fun initPodRefreshAction(): InitPodRefreshAction
    @ContributesAndroidInjector abstract fun podSessionState(): PodSessionState
    @ContributesAndroidInjector abstract fun initPodTask() : InitPodTask

    @ContributesAndroidInjector abstract fun initAapsPodStateManager() : AapsPodStateManager

}
