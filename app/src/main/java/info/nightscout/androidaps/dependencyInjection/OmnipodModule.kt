package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages.InitPodRefreshAction
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.driver.ui.OmnipodUITask

@Module
@Suppress("unused")
abstract class OmnipodModule {

    @ContributesAndroidInjector abstract fun omnipodCommunicationManagerProvider(): OmnipodCommunicationManager
    @ContributesAndroidInjector abstract fun omnipodUITaskProvider(): OmnipodUITask
    @ContributesAndroidInjector abstract fun aapsOmnipodManagerProvider(): AapsOmnipodManager
    @ContributesAndroidInjector abstract fun initPodRefreshAction(): InitPodRefreshAction

}