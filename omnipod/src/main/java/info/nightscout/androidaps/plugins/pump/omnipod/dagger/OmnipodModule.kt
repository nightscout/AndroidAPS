package info.nightscout.androidaps.plugins.pump.omnipod.dagger

import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.data.RLHistoryItemOmnipod
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.omnipod.ui.PodHistoryActivity
import info.nightscout.androidaps.plugins.pump.omnipod.ui.PodManagementActivity
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.initpod.InitActionFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.initpod.InitPodTask
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.pages.InitPodRefreshAction
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.pages.PodInfoFragment
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.removepod.RemoveActionFragment

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
    abstract fun omnipodCommunicationManagerProvider(): OmnipodRileyLinkCommunicationManager

    // Data
    @ContributesAndroidInjector abstract fun initPodRefreshAction(): InitPodRefreshAction
    @ContributesAndroidInjector abstract fun initPodTask(): InitPodTask
    @ContributesAndroidInjector abstract fun rlHistoryItemOmnipod(): RLHistoryItemOmnipod

    companion object {
        @Provides
        fun podStateManagerProvider(aapsPodStateManager: AapsPodStateManager): PodStateManager = aapsPodStateManager
    }
}
