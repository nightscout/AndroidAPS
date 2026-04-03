package app.aaps.plugins.sync.di

import android.content.Context
import androidx.work.WorkManager
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.plugins.sync.garmin.LoopHub
import app.aaps.plugins.sync.garmin.LoopHubImpl
import app.aaps.plugins.sync.nsShared.StoreDataForDbImpl
import app.aaps.plugins.sync.nsShared.compose.NSClientRepositoryImpl
import app.aaps.plugins.sync.nsclient.data.NSSettingsStatusImpl
import app.aaps.plugins.sync.nsclient.data.ProcessedDeviceStatusDataImpl
import app.aaps.plugins.sync.nsclient.services.NSClientService
import app.aaps.plugins.sync.nsclient.workers.NSClientAddAckWorker
import app.aaps.plugins.sync.nsclient.workers.NSClientAddUpdateWorker
import app.aaps.plugins.sync.nsclient.workers.NSClientMbgWorker
import app.aaps.plugins.sync.nsclient.workers.NSClientUpdateRemoveAckWorker
import app.aaps.plugins.sync.nsclientV3.services.NSClientV3Service
import app.aaps.plugins.sync.nsclientV3.workers.DataSyncWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadBgWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadDeviceStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadFoodsWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadLastModificationWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadProfileStoreWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadTreatmentsWorker
import app.aaps.plugins.sync.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.sync.tidepool.auth.AuthFlowIn
import app.aaps.plugins.sync.wear.receivers.WearDataReceiver
import app.aaps.plugins.sync.wear.wearintegration.DataLayerListenerServiceMobile
import app.aaps.plugins.sync.xdrip.DataSyncSelectorXdripImpl
import app.aaps.plugins.sync.xdrip.XdripPlugin
import app.aaps.plugins.sync.xdrip.workers.XdripDataSyncWorker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(
    includes = [
        SyncModule.Binding::class,
        SyncModule.Provide::class,
        SMSCommunicatorModule::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class SyncModule {

    @ContributesAndroidInjector abstract fun contributesNSClientService(): NSClientService
    @ContributesAndroidInjector abstract fun contributesNSClientV3Service(): NSClientV3Service
    @ContributesAndroidInjector abstract fun contributesNSClientWorker(): NSClientAddUpdateWorker
    @ContributesAndroidInjector abstract fun contributesNSClientAddAckWorker(): NSClientAddAckWorker
    @ContributesAndroidInjector abstract fun contributesNSClientUpdateRemoveAckWorker(): NSClientUpdateRemoveAckWorker
    @ContributesAndroidInjector abstract fun contributesNSClientMbgWorker(): NSClientMbgWorker

    @ContributesAndroidInjector abstract fun contributesLoadStatusWorker(): LoadStatusWorker
    @ContributesAndroidInjector abstract fun contributesLoadLastModificationWorker(): LoadLastModificationWorker
    @ContributesAndroidInjector abstract fun contributesLoadBgWorker(): LoadBgWorker
    @ContributesAndroidInjector abstract fun contributesLoadFoodsWorker(): LoadFoodsWorker
    @ContributesAndroidInjector abstract fun contributesLoadProfileStoreWorker(): LoadProfileStoreWorker
    @ContributesAndroidInjector abstract fun contributesTreatmentWorker(): LoadTreatmentsWorker
    @ContributesAndroidInjector abstract fun contributesLoadDeviceStatusWorker(): LoadDeviceStatusWorker
    @ContributesAndroidInjector abstract fun contributesDataSyncWorker(): DataSyncWorker

    @ContributesAndroidInjector abstract fun contributesAuthFlowInActivity(): AuthFlowIn
    @ContributesAndroidInjector abstract fun contributesXdripDataSyncWorker(): XdripDataSyncWorker
    @ContributesAndroidInjector abstract fun contributesWearDataReceiver(): WearDataReceiver
    @ContributesAndroidInjector abstract fun contributesWatchUpdaterService(): DataLayerListenerServiceMobile

    @Module
    @InstallIn(SingletonComponent::class)
    open class Provide {

        @Reusable
        @Provides
        fun providesWorkManager(context: Context) = WorkManager.getInstance(context)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface Binding {

        @Binds fun bindProcessedDeviceStatusData(processedDeviceStatusDataImpl: ProcessedDeviceStatusDataImpl): ProcessedDeviceStatusData
        @Binds fun bindNSSettingsStatus(nsSettingsStatusImpl: NSSettingsStatusImpl): NSSettingsStatus
        @Binds fun bindDataSyncSelectorXdripInterface(dataSyncSelectorXdripImpl: DataSyncSelectorXdripImpl): DataSyncSelectorXdrip
        @Binds fun bindStoreDataForDb(storeDataForDbImpl: StoreDataForDbImpl): StoreDataForDb
        @Binds fun bindSmsCommunicator(smsCommunicatorPlugin: SmsCommunicatorPlugin): SmsCommunicator
        @Binds fun bindXDripBroadcastInterface(xDripBroadcastImpl: XdripPlugin): XDripBroadcast
        @Binds fun bindLoopHub(loopHub: LoopHubImpl): LoopHub

        @Binds fun bindNSClientRepository(nsClientRepositoryImpl: NSClientRepositoryImpl): NSClientRepository
    }

}