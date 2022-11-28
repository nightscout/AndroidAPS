package info.nightscout.plugins.sync.di

import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.interfaces.nsclient.NSSettingsStatus
import info.nightscout.interfaces.nsclient.ProcessedDeviceStatusData
import info.nightscout.interfaces.nsclient.StoreDataForDb
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.plugins.sync.nsShared.NSClientFragment
import info.nightscout.plugins.sync.nsShared.StoreDataForDbImpl
import info.nightscout.plugins.sync.nsclient.DataSyncSelectorImplementation
import info.nightscout.plugins.sync.nsclient.data.NSSettingsStatusImpl
import info.nightscout.plugins.sync.nsclient.data.ProcessedDeviceStatusDataImpl
import info.nightscout.plugins.sync.nsclient.services.NSClientService
import info.nightscout.plugins.sync.nsclient.workers.NSClientAddAckWorker
import info.nightscout.plugins.sync.nsclient.workers.NSClientAddUpdateWorker
import info.nightscout.plugins.sync.nsclient.workers.NSClientMbgWorker
import info.nightscout.plugins.sync.nsclient.workers.NSClientUpdateRemoveAckWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadBgWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadDeviceStatusWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadLastModificationWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadStatusWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadTreatmentsWorker
import info.nightscout.plugins.sync.nsclientV3.workers.ProcessTreatmentsWorker
import info.nightscout.plugins.sync.tidepool.TidepoolFragment

@Module(
    includes = [
        SyncModule.Binding::class
    ]
)

@Suppress("unused")
abstract class SyncModule {

    @ContributesAndroidInjector abstract fun contributesNSClientFragment(): NSClientFragment

    @ContributesAndroidInjector abstract fun contributesNSClientService(): NSClientService
    @ContributesAndroidInjector abstract fun contributesNSClientWorker(): NSClientAddUpdateWorker
    @ContributesAndroidInjector abstract fun contributesNSClientAddAckWorker(): NSClientAddAckWorker
    @ContributesAndroidInjector abstract fun contributesNSClientUpdateRemoveAckWorker(): NSClientUpdateRemoveAckWorker
    @ContributesAndroidInjector abstract fun contributesNSClientMbgWorker(): NSClientMbgWorker

    @ContributesAndroidInjector abstract fun contributesLoadStatusWorker(): LoadStatusWorker
    @ContributesAndroidInjector abstract fun contributesLoadLastModificationWorker(): LoadLastModificationWorker
    @ContributesAndroidInjector abstract fun contributesLoadBgWorker(): LoadBgWorker
    @ContributesAndroidInjector abstract fun contributesStoreBgWorker(): StoreDataForDbImpl.StoreBgWorker
    @ContributesAndroidInjector abstract fun contributesTreatmentWorker(): LoadTreatmentsWorker
    @ContributesAndroidInjector abstract fun contributesProcessTreatmentsWorker(): ProcessTreatmentsWorker
    @ContributesAndroidInjector abstract fun contributesLoadDeviceStatusWorker(): LoadDeviceStatusWorker

    @ContributesAndroidInjector abstract fun contributesTidepoolFragment(): TidepoolFragment

    @Module
    interface Binding {

        @Binds fun bindProcessedDeviceStatusData(processedDeviceStatusDataImpl: ProcessedDeviceStatusDataImpl): ProcessedDeviceStatusData
        @Binds fun bindNSSettingsStatus(nsSettingsStatusImpl: NSSettingsStatusImpl): NSSettingsStatus
        @Binds fun bindDataSyncSelectorInterface(dataSyncSelectorImplementation: DataSyncSelectorImplementation): DataSyncSelector
        @Binds fun bindStoreDataForDb(storeDataForDbImpl: StoreDataForDbImpl): StoreDataForDb
    }

}