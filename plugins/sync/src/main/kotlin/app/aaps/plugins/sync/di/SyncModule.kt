package app.aaps.plugins.sync.di

import android.content.Context
import androidx.work.WorkManager
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.plugins.sync.garmin.LoopHub
import app.aaps.plugins.sync.garmin.LoopHubImpl
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.StoreDataForDbImpl
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlRoundTrip
import app.aaps.plugins.sync.nsclientV3.compose.NSClientRepositoryImpl
import app.aaps.plugins.sync.nsclientV3.data.ProcessedDeviceStatusDataImpl
import app.aaps.plugins.sync.nsclientV3.services.NSClientV3Service
import app.aaps.plugins.sync.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.sync.xdrip.XdripPlugin
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module(
    includes = [
        SyncModule.Binding::class,
        SyncModule.Provide::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class SyncModule {

    // NSClient / NSClientV3 / Xdrip sync workers migrated to @HiltWorker (constructed by HiltWorkerFactory).
    // These two stay on dagger.android: both hit a Metro codegen bug when member injected, because each
    // needs a Metro built plugin - see https://github.com/ZacSweers/metro/issues/2731.
    @ContributesAndroidInjector abstract fun contributesNSClientV3Service(): NSClientV3Service

    @Module
    @InstallIn(SingletonComponent::class)
    open class Provide {

        // Was @Reusable. Metro does not support it, and this module turns on Dagger interop so Metro
        // validates every Dagger annotation here. WorkManager.getInstance already returns one
        // instance, so @Singleton is the same behaviour.
        @Singleton
        @Provides
        fun providesWorkManager(context: Context): WorkManager = WorkManager.getInstance(context)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface Binding {

        @Binds fun bindProcessedDeviceStatusData(processedDeviceStatusDataImpl: ProcessedDeviceStatusDataImpl): ProcessedDeviceStatusData
        @Binds fun bindStoreDataForDb(storeDataForDbImpl: StoreDataForDbImpl): StoreDataForDb
        @Binds fun bindSmsCommunicator(smsCommunicatorPlugin: SmsCommunicatorPlugin): SmsCommunicator
        @Binds fun bindXDripBroadcastInterface(xDripBroadcastImpl: XdripPlugin): XDripBroadcast
        @Binds fun bindLoopHub(loopHub: LoopHubImpl): LoopHub
        @Binds fun bindNsClient(nsClientV3Plugin: NSClientV3Plugin): NsClient

        @Binds fun bindNSClientRepository(nsClientRepositoryImpl: NSClientRepositoryImpl): NSClientRepository

        @Binds fun bindClientControlActionDispatcher(roundTrip: ClientControlRoundTrip): ClientControlActionDispatcher
    }

}