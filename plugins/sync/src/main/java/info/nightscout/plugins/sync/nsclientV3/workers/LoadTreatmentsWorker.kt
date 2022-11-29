package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.interfaces.nsclient.StoreDataForDb
import info.nightscout.plugins.sync.nsShared.StoreDataForDbImpl
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class LoadTreatmentsWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var context: Context
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var storeDataForDb: StoreDataForDbImpl

    override fun doWork(): Result {
        var ret = Result.success()

        runBlocking {
            if ((nsClientV3Plugin.lastModified?.collections?.treatments ?: Long.MAX_VALUE) > nsClientV3Plugin.lastFetched.collections.treatments)
                try {
                    val treatments = nsClientV3Plugin.nsAndroidClient.getTreatmentsModifiedSince(nsClientV3Plugin.lastFetched.collections.treatments, 500)
                    aapsLogger.debug("TREATMENTS: $treatments")
                    if (treatments.isNotEmpty()) {
                        rxBus.send(
                            EventNSClientNewLog(
                                "RCV",
                                "${treatments.size} TRs from ${dateUtil.dateAndTimeAndSecondsString(nsClientV3Plugin.lastFetched.collections.treatments)}"
                            )
                        )
                        // Schedule processing of fetched data and continue of loading
                        WorkManager.getInstance(context)
                            .beginUniqueWork(
                                NSClientV3Plugin.JOB_NAME,
                                ExistingWorkPolicy.APPEND_OR_REPLACE,
                                OneTimeWorkRequest.Builder(ProcessTreatmentsWorker::class.java)
                                    .setInputData(dataWorkerStorage.storeInputData(treatments))
                                    .build()
                            ).then(OneTimeWorkRequest.Builder(LoadTreatmentsWorker::class.java).build())
                            .enqueue()
                    } else {
                        rxBus.send(
                            EventNSClientNewLog(
                                "END", "No TRs from ${dateUtil.dateAndTimeAndSecondsString(nsClientV3Plugin.lastFetched.collections.treatments)}"
                            )
                        )
                        storeDataForDb.storeTreatmentsToDb()
                        WorkManager.getInstance(context)
                            .enqueueUniqueWork(
                                NSClientV3Plugin.JOB_NAME,
                                ExistingWorkPolicy.APPEND_OR_REPLACE,
                                OneTimeWorkRequest.Builder(LoadDeviceStatusWorker::class.java).build()
                            )
                    }
                } catch (error: Exception) {
                    aapsLogger.error("Error: ", error)
                    ret = Result.failure(workDataOf("Error" to error.toString()))
                }
            else {
                rxBus.send(EventNSClientNewLog("END", "No new TRs from ${dateUtil.dateAndTimeAndSecondsString(nsClientV3Plugin.lastFetched.collections.treatments)}"))
                storeDataForDb.storeTreatmentsToDb()
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        NSClientV3Plugin.JOB_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        OneTimeWorkRequest.Builder(LoadDeviceStatusWorker::class.java).build()
                    )
            }
        }
        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}