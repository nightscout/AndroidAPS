package info.nightscout.androidaps.plugins.pump.omnipod.eros.dagger

import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.eros.data.RLHistoryItemOmnipod
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsErosPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.ErosPodHistoryActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.ErosPodManagementActivity

@Module
@Suppress("unused")
abstract class OmnipodErosModule {

    // Activities
    @ContributesAndroidInjector
    abstract fun contributesPodManagementActivity(): ErosPodManagementActivity
    @ContributesAndroidInjector abstract fun contributesPodHistoryActivity(): ErosPodHistoryActivity

    // Service
    @ContributesAndroidInjector
    abstract fun omnipodCommunicationManagerProvider(): OmnipodRileyLinkCommunicationManager

    // Data
    @ContributesAndroidInjector abstract fun rlHistoryItemOmnipod(): RLHistoryItemOmnipod

    companion object {

        @Provides
        fun podStateManagerProvider(aapsErosPodStateManager: AapsErosPodStateManager): PodStateManager = aapsErosPodStateManager
    }
}
