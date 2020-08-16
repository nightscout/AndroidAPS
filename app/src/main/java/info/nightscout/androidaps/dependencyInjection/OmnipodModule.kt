package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
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
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.driver.ui.OmnipodUITask
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Singleton

@Module
@Suppress("unused")
abstract class OmnipodModule {

    // Activities
    @ContributesAndroidInjector
    abstract fun contributesPodManagementActivity(): info.nightscout.androidaps.plugins.pump.omnipod.dialogs.PodManagementActivity
    @ContributesAndroidInjector abstract fun contributesPodHistoryActivity(): info.nightscout.androidaps.plugins.pump.omnipod.dialogs.PodHistoryActivity

    // Fragments
    @ContributesAndroidInjector abstract fun initActionFragment(): info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod.InitActionFragment
    @ContributesAndroidInjector abstract fun removeActionFragment(): info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.removepod.RemoveActionFragment
    @ContributesAndroidInjector abstract fun podInfoFragment(): info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages.PodInfoFragment

    // Service
    @ContributesAndroidInjector
    abstract fun omnipodCommunicationManagerProvider(): info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager
    @ContributesAndroidInjector abstract fun aapsOmnipodManagerProvider(): info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsOmnipodManager

    // Data
    @ContributesAndroidInjector abstract fun omnipodUITaskProvider(): info.nightscout.androidaps.plugins.pump.omnipod.driver.ui.OmnipodUITask
    @ContributesAndroidInjector abstract fun initPodRefreshAction(): info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages.InitPodRefreshAction
    @ContributesAndroidInjector abstract fun podStateManager(): info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager
    @ContributesAndroidInjector abstract fun initPodTask(): info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod.InitPodTask
    @ContributesAndroidInjector abstract fun omnipodPumpPlugin(): info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin

    companion object {
        @Provides
        @Singleton
        fun podStateManagerProvider(aapsLogger: AAPSLogger, sp: SP, omnipodPumpStatus: info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus,
                                    rxBus: RxBusWrapper, resourceHelper: ResourceHelper, dateUtil: DateUtil): info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager =
            info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsPodStateManager(aapsLogger, sp, omnipodPumpStatus, rxBus, resourceHelper, dateUtil)
    }
}
