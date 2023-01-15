package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.interfaces.workflow.WorkerClasses
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.rx.logging.LTag
import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.max

class LoadProfileStoreWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var context: Context
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var workerClasses: WorkerClasses

    override suspend fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))

        try {
            val lastLoaded = max(nsClientV3Plugin.lastLoadedSrvModified.collections.profile, 0)
            if ((nsClientV3Plugin.newestDataOnServer?.collections?.profile ?: Long.MAX_VALUE) > lastLoaded) {
                val response: NSAndroidClient.ReadResponse<List<JSONObject>> = nsAndroidClient.getLastProfileStore()
                val profiles = response.values
                if (profiles.size == 1) {
                    val profile = profiles[0]
                    JsonHelper.safeGetLongAllowNull(profile, "srvModified")?.let { nsClientV3Plugin.lastLoadedSrvModified.collections.profile = it }
                    nsClientV3Plugin.storeLastLoadedSrvModified()
                    aapsLogger.debug(LTag.NSCLIENT, "PROFILE: $profile")
                    rxBus.send(EventNSClientNewLog("RCV", "1 PROFILE from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                    WorkManager.getInstance(context)
                        .beginUniqueWork(
                            NSClientV3Plugin.JOB_NAME,
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            OneTimeWorkRequest.Builder((workerClasses.nsProfileWorker))
                                .setInputData(dataWorkerStorage.storeInputData(profile))
                                .build()
                        ).then(OneTimeWorkRequest.Builder(LoadDeviceStatusWorker::class.java).build())
                        .enqueue()
                } else {
                    rxBus.send(EventNSClientNewLog("RCV END", "No new PROFILE from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                    WorkManager.getInstance(context)
                        .enqueueUniqueWork(
                            NSClientV3Plugin.JOB_NAME,
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            OneTimeWorkRequest.Builder(LoadDeviceStatusWorker::class.java).build()
                        )
                }
            } else {
                rxBus.send(EventNSClientNewLog("RCV END", "No PROFILE from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        NSClientV3Plugin.JOB_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        OneTimeWorkRequest.Builder(LoadDeviceStatusWorker::class.java).build()
                    )
            }
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            rxBus.send(EventNSClientNewLog("ERROR", error.localizedMessage))
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }

        return Result.success()
    }
}