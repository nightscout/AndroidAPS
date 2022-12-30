package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.interfaces.nsclient.StoreDataForDb
import info.nightscout.interfaces.workflow.WorkerClasses
import info.nightscout.plugins.sync.nsShared.StoreDataForDbImpl
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.sdk.localmodel.food.NSFood
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class LoadFoodsWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var context: Context
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var storeDataForDb: StoreDataForDb
    @Inject lateinit var workerClasses: WorkerClasses

    override fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))

        // Food database doesn't provide last record modification
        // Read full collection every 5th attempt
        runBlocking {
            if (nsClientV3Plugin.lastLoadedSrvModified.collections.foods++ % 5 == 0L) {
                val foods: List<NSFood> = nsAndroidClient.getFoods(1000)
                aapsLogger.debug("FOODS: $foods")
                rxBus.send(EventNSClientNewLog("RCV", "${foods.size} FOODs"))
                // Schedule processing of fetched data
                WorkManager.getInstance(context)
                    .beginUniqueWork(
                        NSClientV3Plugin.JOB_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        OneTimeWorkRequest.Builder(ProcessFoodWorker::class.java)
                            .setInputData(dataWorkerStorage.storeInputData(foods))
                            .build()
                    ).then(OneTimeWorkRequest.Builder(StoreDataForDbImpl.StoreFoodWorker::class.java).build())
                    .then(OneTimeWorkRequest.Builder(LoadDeviceStatusWorker::class.java).build())
                    .enqueue()
            } else {
                rxBus.send(EventNSClientNewLog("RCV", "FOOD skipped"))
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        NSClientV3Plugin.JOB_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        OneTimeWorkRequest.Builder(LoadDeviceStatusWorker::class.java).build()
                    )
            }
        }
        return Result.success()
    }
}