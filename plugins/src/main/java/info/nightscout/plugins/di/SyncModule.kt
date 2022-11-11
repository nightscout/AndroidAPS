package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.sync.nsShared.NSClientFragment
import info.nightscout.plugins.sync.nsShared.StoreDataForDb
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

@Module
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
    @ContributesAndroidInjector abstract fun contributesStoreBgWorker(): StoreDataForDb.StoreBgWorker
    @ContributesAndroidInjector abstract fun contributesTreatmentWorker(): LoadTreatmentsWorker
    @ContributesAndroidInjector abstract fun contributesProcessTreatmentsWorker(): ProcessTreatmentsWorker
    @ContributesAndroidInjector abstract fun contributesLoadDeviceStatusWorker(): LoadDeviceStatusWorker

    @ContributesAndroidInjector abstract fun contributesTidepoolFragment(): TidepoolFragment
}