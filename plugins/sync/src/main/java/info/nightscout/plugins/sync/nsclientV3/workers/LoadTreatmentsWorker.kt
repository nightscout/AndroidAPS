package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.core.utils.worker.then
import info.nightscout.interfaces.nsclient.StoreDataForDb
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.sdk.localmodel.treatment.NSTreatment
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.math.max

class LoadTreatmentsWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var context: Context
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var storeDataForDb: StoreDataForDb

    override suspend fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))
        var ret = Result.success()

        val isFirstLoad = nsClientV3Plugin.isFirstLoad(NsClient.Collection.TREATMENTS)
        val lastLoaded =
            if (isFirstLoad) max(nsClientV3Plugin.firstLoadContinueTimestamp.collections.treatments, dateUtil.now() - nsClientV3Plugin.maxAge)
            else max(nsClientV3Plugin.lastLoadedSrvModified.collections.treatments, dateUtil.now() - nsClientV3Plugin.maxAge)
        runBlocking {
            if ((nsClientV3Plugin.newestDataOnServer?.collections?.treatments ?: Long.MAX_VALUE) > lastLoaded)
                try {
                    val treatments: List<NSTreatment>
                    val response: NSAndroidClient.ReadResponse<List<NSTreatment>>?
                    if (isFirstLoad) {
                        val lastLoadedIso = dateUtil.toISOString(lastLoaded)
                        response = nsAndroidClient.getTreatmentsNewerThan(lastLoadedIso, 500)
                    }
                    else {
                        response = nsAndroidClient.getTreatmentsModifiedSince(lastLoaded, 500)
                        response.lastServerModified?.let { nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = it }
                        nsClientV3Plugin.storeLastLoadedSrvModified()
                    }
                    treatments = response.values
                    aapsLogger.debug("TREATMENTS: $treatments")
                    if (treatments.isNotEmpty()) {
                        val action = if (isFirstLoad) "RCV-FIRST" else "RCV"
                        rxBus.send(EventNSClientNewLog(action, "${treatments.size} TRs from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                        // Schedule processing of fetched data and continue of loading
                        WorkManager.getInstance(context)
                            .beginUniqueWork(
                                NSClientV3Plugin.JOB_NAME,
                                ExistingWorkPolicy.APPEND_OR_REPLACE,
                                OneTimeWorkRequest.Builder(ProcessTreatmentsWorker::class.java)
                                    .setInputData(dataWorkerStorage.storeInputData(response))
                                    .build()
                            )
                            // response 304 == Not modified (happens when date > srvModified => bad time on phone or server during upload
                            .then(response.code != 304, OneTimeWorkRequest.Builder(LoadTreatmentsWorker::class.java).build())
                            .then(response.code == 304, OneTimeWorkRequest.Builder(LoadFoodsWorker::class.java).build())
                            .enqueue()
                    } else {
                        // End first load
                        if (isFirstLoad) {
                            nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = lastLoaded
                            nsClientV3Plugin.storeLastLoadedSrvModified()
                        }
                        rxBus.send(EventNSClientNewLog("RCV END", "No TRs from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                        storeDataForDb.storeTreatmentsToDb()
                        WorkManager.getInstance(context)
                            .enqueueUniqueWork(
                                NSClientV3Plugin.JOB_NAME,
                                ExistingWorkPolicy.APPEND_OR_REPLACE,
                                OneTimeWorkRequest.Builder(LoadFoodsWorker::class.java).build()
                            )
                    }
                } catch (error: Exception) {
                    aapsLogger.error("Error: ", error)
                    ret = Result.failure(workDataOf("Error" to error.toString()))
                }
            else {
                // End first load
                if (isFirstLoad) {
                    nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = lastLoaded
                    nsClientV3Plugin.storeLastLoadedSrvModified()
                }
                rxBus.send(EventNSClientNewLog("RCV END", "No new TRs from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                storeDataForDb.storeTreatmentsToDb()
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        NSClientV3Plugin.JOB_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        OneTimeWorkRequest.Builder(LoadFoodsWorker::class.java).build()
                    )
            }
        }
        return ret
    }
}