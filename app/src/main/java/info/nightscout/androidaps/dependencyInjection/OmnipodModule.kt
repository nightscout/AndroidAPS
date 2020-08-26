package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.data.RLHistoryItemOmnipod
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.OmnipodRileyLinkCommunicationManager
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
    @ContributesAndroidInjector abstract fun aapsOmnipodManagerProvider(): AapsOmnipodManager

    // Data
    @ContributesAndroidInjector abstract fun initPodRefreshAction(): InitPodRefreshAction
    @ContributesAndroidInjector abstract fun podStateManager(): PodStateManager
    @ContributesAndroidInjector abstract fun initPodTask(): InitPodTask
    @ContributesAndroidInjector abstract fun omnipodPumpPlugin(): OmnipodPumpPlugin
    @ContributesAndroidInjector abstract fun contributesRLHistoryItemOmnipod(): RLHistoryItemOmnipod

    companion object {
        @Provides
        fun podStateManagerProvider(aapsPodStateManager: AapsPodStateManager): PodStateManager = aapsPodStateManager
    }
}
