package info.nightscout.androidaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.NsClient
import info.nightscout.androidaps.plugins.source.NSClientSourcePlugin
import info.nightscout.androidaps.plugins.sync.nsShared.StoreDataForDb
import info.nightscout.androidaps.plugins.sync.nsShared.events.EventNSClientNewLog
import info.nightscout.androidaps.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.androidaps.receivers.DataWorkerStorage
import info.nightscout.shared.utils.DateUtil
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class LoadBgWorker(
    context: Context, params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var context: Context
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin

    companion object {

        val JOB_NAME: String = this::class.java.simpleName
    }

    override fun doWork(): Result {
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
                                "${sgvs.size} SVGs from ${dateUtil.dateAndTimeAndSecondsString(nsClientV3Plugin.lastFetched.collections.entries)}",
                                NsClient.Version.V3
                            )
                        )
                        // Objective0
                        sp.putBoolean(R.string.key_ObjectivesbgIsAvailableInNS, true)
                        // Schedule processing of fetched data and continue of loading
                        WorkManager.getInstance(context).beginUniqueWork(
                            JOB_NAME,
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            OneTimeWorkRequest.Builder(NSClientSourcePlugin.NSClientSourceWorker::class.java).setInputData(dataWorkerStorage.storeInputData(sgvs)).build()
                        ).then(OneTimeWorkRequest.Builder(LoadBgWorker::class.java).build()).enqueue()
                    } else {
                        rxBus.send(EventNSClientNewLog("END", "No SGVs from ${dateUtil.dateAndTimeAndSecondsString(nsClientV3Plugin.lastFetched.collections.entries)}", NsClient.Version.V3))
                        WorkManager.getInstance(context)
                            .beginUniqueWork(
                                NSClientV3Plugin.JOB_NAME,
                                ExistingWorkPolicy.APPEND_OR_REPLACE,
                                OneTimeWorkRequest.Builder(StoreDataForDb.StoreBgWorker::class.java).build()
                            )
                            .then(OneTimeWorkRequest.Builder(LoadTreatmentsWorker::class.java).build())
                            .enqueue()
                    }
                } catch (error: Exception) {
                    aapsLogger.error("Error: ", error)
                    ret = Result.failure(workDataOf("Error" to error.toString()))
                }
            else {
                rxBus.send(EventNSClientNewLog("END", "No new SGVs from ${dateUtil.dateAndTimeAndSecondsString(nsClientV3Plugin.lastFetched.collections.entries)}", NsClient.Version.V3))
                WorkManager.getInstance(context)
                    .beginUniqueWork(
                        NSClientV3Plugin.JOB_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        OneTimeWorkRequest.Builder(StoreDataForDb.StoreBgWorker::class.java).build()
                    )
                    .then(OneTimeWorkRequest.Builder(LoadTreatmentsWorker::class.java).build())
                    .enqueue()
            }
        }
        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}