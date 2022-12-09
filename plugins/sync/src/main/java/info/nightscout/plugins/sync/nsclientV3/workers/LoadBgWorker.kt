package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.interfaces.workflow.WorkerClasses
import info.nightscout.plugins.sync.nsShared.StoreDataForDbImpl
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

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
        var ret = Result.success()

        runBlocking {
            if ((nsClientV3Plugin.lastModified?.collections?.entries ?: Long.MAX_VALUE) > nsClientV3Plugin.lastFetched.collections.entries)
                try {
                    //val sgvs = nsClientV3Plugin.nsAndroidClient.getSgvsModifiedSince(nsClientV3Plugin.lastFetched.collections.entries)
                    val sgvs = nsClientV3Plugin.nsAndroidClient.getSgvsNewerThan(nsClientV3Plugin.lastFetched.collections.entries, 500)
                    aapsLogger.debug("SGVS: $sgvs")
                    if (sgvs.isNotEmpty()) {
                        rxBus.send(
                            EventNSClientNewLog(
                                "RCV",
                                "${sgvs.size} SVGs from ${dateUtil.dateAndTimeAndSecondsString(nsClientV3Plugin.lastFetched.collections.entries)}"
                            )
                        )
                        // Objective0
                        sp.putBoolean(info.nightscout.core.utils.R.string.key_objectives_bg_is_available_in_ns, true)
                        // Schedule processing of fetched data and continue of loading
                        WorkManager.getInstance(context).beginUniqueWork(
                            JOB_NAME,
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            OneTimeWorkRequest.Builder(workerClasses.nsClientSourceWorker).setInputData(dataWorkerStorage.storeInputData(sgvs)).build()
                        ).then(OneTimeWorkRequest.Builder(LoadBgWorker::class.java).build()).enqueue()
                    } else {
                        rxBus.send(EventNSClientNewLog("END", "No SGVs from ${dateUtil.dateAndTimeAndSecondsString(nsClientV3Plugin.lastFetched.collections.entries)}"))
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
                rxBus.send(EventNSClientNewLog("END", "No new SGVs from ${dateUtil.dateAndTimeAndSecondsString(nsClientV3Plugin.lastFetched.collections.entries)}"))
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