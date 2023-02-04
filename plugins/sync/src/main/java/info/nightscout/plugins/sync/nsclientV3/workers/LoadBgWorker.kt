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
import info.nightscout.interfaces.source.NSClientSource
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.interfaces.workflow.WorkerClasses
import info.nightscout.plugins.sync.nsShared.StoreDataForDbImpl
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.rx.logging.LTag
import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.sdk.localmodel.entry.NSSgvV3
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlin.math.max

class LoadBgWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var context: Context
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var nsClientSource: NSClientSource
    @Inject lateinit var workerClasses: WorkerClasses
    @Inject lateinit var workManager: WorkManager

    override suspend fun doWorkAndLog(): Result {
        if (!nsClientSource.isEnabled() && !sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_cgm, false)) {
            workManager
                .enqueueUniqueWork(
                    NSClientV3Plugin.JOB_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequest.Builder(LoadTreatmentsWorker::class.java).build()
                )
            return Result.success(workDataOf("Result" to "Load not enabled"))
        }

        try {
            val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))
            val isFirstLoad = nsClientV3Plugin.isFirstLoad(NsClient.Collection.ENTRIES)
            val lastLoaded =
                if (isFirstLoad) max(nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries, dateUtil.now() - nsClientV3Plugin.maxAge)
                else max(nsClientV3Plugin.lastLoadedSrvModified.collections.entries, dateUtil.now() - nsClientV3Plugin.maxAge)
            if ((nsClientV3Plugin.newestDataOnServer?.collections?.entries ?: Long.MAX_VALUE) > lastLoaded) {
                val sgvs: List<NSSgvV3>
                val response: NSAndroidClient.ReadResponse<List<NSSgvV3>>?
                if (isFirstLoad) response = nsAndroidClient.getSgvsNewerThan(lastLoaded, NSClientV3Plugin.RECORDS_TO_LOAD)
                else {
                    response = nsAndroidClient.getSgvsModifiedSince(lastLoaded, NSClientV3Plugin.RECORDS_TO_LOAD)
                    aapsLogger.debug(LTag.NSCLIENT, "lastLoadedSrvModified: ${response.lastServerModified}")
                    response.lastServerModified?.let { nsClientV3Plugin.lastLoadedSrvModified.collections.entries = it }
                    nsClientV3Plugin.storeLastLoadedSrvModified()
                    nsClientV3Plugin.scheduleIrregularExecution() // Idea is to run after 5 min after last BG
                }
                sgvs = response.values
                aapsLogger.debug(LTag.NSCLIENT, "SGVS: $sgvs")
                if (sgvs.isNotEmpty()) {
                    val action = if (isFirstLoad) "RCV-FIRST" else "RCV"
                    rxBus.send(EventNSClientNewLog(action, "${sgvs.size} SVGs from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                    // Objective0
                    sp.putBoolean(info.nightscout.core.utils.R.string.key_objectives_bg_is_available_in_ns, true)
                    // Schedule processing of fetched data and continue of loading
                    val stopLoading = sgvs.size != NSClientV3Plugin.RECORDS_TO_LOAD || response.code == 304
                    workManager
                        .beginUniqueWork(
                            NSClientV3Plugin.JOB_NAME,
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            OneTimeWorkRequest.Builder(workerClasses.nsClientSourceWorker).setInputData(dataWorkerStorage.storeInputData(sgvs)).build()
                        )
                        // response 304 == Not modified (happens when date > srvModified => bad time on phone or server during upload
                        .then(!stopLoading, OneTimeWorkRequest.Builder(LoadBgWorker::class.java).build())
                        .then(stopLoading, OneTimeWorkRequest.Builder(LoadTreatmentsWorker::class.java).build())
                        .enqueue()
                } else {
                    // End first load
                    if (isFirstLoad) {
                        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = lastLoaded
                        nsClientV3Plugin.storeLastLoadedSrvModified()
                    }
                    rxBus.send(EventNSClientNewLog("RCV END", "No SGVs from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                    workManager
                        .beginUniqueWork(
                            NSClientV3Plugin.JOB_NAME,
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            OneTimeWorkRequest.Builder(StoreDataForDbImpl.StoreBgWorker::class.java).build()
                        )
                        .then(OneTimeWorkRequest.Builder(LoadTreatmentsWorker::class.java).build())
                        .enqueue()
                }
            } else {
                // End first load
                if (isFirstLoad) {
                    nsClientV3Plugin.lastLoadedSrvModified.collections.entries = lastLoaded
                    nsClientV3Plugin.storeLastLoadedSrvModified()
                }
                rxBus.send(EventNSClientNewLog("RCV END", "No new SGVs from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                workManager
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
            rxBus.send(EventNSClientNewLog("ERROR", error.localizedMessage))
            nsClientV3Plugin.lastOperationError = error.localizedMessage
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }

        nsClientV3Plugin.lastOperationError = null
        return Result.success()
    }
}