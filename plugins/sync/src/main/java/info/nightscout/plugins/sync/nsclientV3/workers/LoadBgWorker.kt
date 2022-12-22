package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.interfaces.workflow.WorkerClasses
import info.nightscout.plugins.sync.nsShared.StoreDataForDbImpl
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.sdk.localmodel.entry.NSSgvV3
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.math.max

class LoadBgWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var context: Context
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var workerClasses: WorkerClasses

    companion object {

        val JOB_NAME: String = this::class.java.simpleName
    }

    override fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))
        var ret = Result.success()
        val isFirstLoad = nsClientV3Plugin.isFirstLoad(NsClient.Collection.ENTRIES)
        val lastLoaded =
            if (isFirstLoad) max(nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries, dateUtil.now() - nsClientV3Plugin.maxAge)
            else max(nsClientV3Plugin.lastLoadedSrvModified.collections.entries, dateUtil.now() - nsClientV3Plugin.maxAge)
        runBlocking {
            if ((nsClientV3Plugin.newestDataOnServer?.collections?.entries ?: Long.MAX_VALUE) > lastLoaded)
                try {
                    val sgvs: List<NSSgvV3>
                    val response: NSAndroidClient.ReadResponse<List<NSSgvV3>>?
                    if (isFirstLoad) sgvs = nsAndroidClient.getSgvsNewerThan(lastLoaded, 500)
                    else {
                        response = nsAndroidClient.getSgvsModifiedSince(lastLoaded, 500)
                        sgvs = response.values
                        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = response.lastServerModified
                        nsClientV3Plugin.storeLastFetched()
                    }
                    aapsLogger.debug("SGVS: $sgvs")
                    if (sgvs.isNotEmpty()) {
                        val action = if (isFirstLoad) "RCV-FIRST" else "RCV"
                        rxBus.send(EventNSClientNewLog(action, "${sgvs.size} SVGs from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                        // Objective0
                        sp.putBoolean(info.nightscout.core.utils.R.string.key_objectives_bg_is_available_in_ns, true)
                        // Schedule processing of fetched data and continue of loading
                        WorkManager.getInstance(context).beginUniqueWork(
                            JOB_NAME,
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            OneTimeWorkRequest.Builder(workerClasses.nsClientSourceWorker).setInputData(dataWorkerStorage.storeInputData(sgvs)).build()
                        ).then(OneTimeWorkRequest.Builder(LoadBgWorker::class.java).build()).enqueue()
                    } else {
                        // End first load
                        if (isFirstLoad) {
                            nsClientV3Plugin.lastLoadedSrvModified.collections.entries = lastLoaded
                            nsClientV3Plugin.storeLastFetched()
                        }
                        rxBus.send(EventNSClientNewLog("RCV END", "No SGVs from ${dateUtil
                            .dateAndTimeAndSecondsString(lastLoaded)}"))
                        WorkManager.getInstance(context)
                            .beginUniqueWork(
                                NSClientV3Plugin.JOB_NAME,
                                ExistingWorkPolicy.APPEND_OR_REPLACE,
                                OneTimeWorkRequest.Builder(StoreDataForDbImpl.StoreBgWorker::class.java).build()
                            )
                            .then(OneTimeWorkRequest.Builder(LoadTreatmentsWorker::class.java).build())
                            .enqueue()
                    }
                } catch (error: Exception) {
                    aapsLogger.error("Error: ", error)
                    ret = Result.failure(workDataOf("Error" to error.toString()))
                }
            else {
                // End first load
                if (isFirstLoad) {
                    nsClientV3Plugin.lastLoadedSrvModified.collections.entries = lastLoaded
                    nsClientV3Plugin.storeLastFetched()
                }
                rxBus.send(EventNSClientNewLog("RCV END", "No new SGVs from ${dateUtil
                    .dateAndTimeAndSecondsString(lastLoaded)}"))
                nsClientV3Plugin.scheduleNewExecution() // Idea is to run after 5 min after last BG
                WorkManager.getInstance(context)
                    .beginUniqueWork(
                        NSClientV3Plugin.JOB_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        OneTimeWorkRequest.Builder(StoreDataForDbImpl.StoreBgWorker::class.java).build()
                    )
                    .then(OneTimeWorkRequest.Builder(LoadTreatmentsWorker::class.java).build())
                    .enqueue()
            }
        }
        return ret
    }
}