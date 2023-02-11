package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.interfaces.nsclient.StoreDataForDb
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.plugins.sync.nsShared.NsIncomingDataProcessor
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.rx.logging.LTag
import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.sdk.localmodel.treatment.NSTreatment
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlin.math.max

class LoadTreatmentsWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var context: Context
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var storeDataForDb: StoreDataForDb
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor

    override suspend fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))

        var continueLoading = true
        try {
            while (continueLoading) {
                val isFirstLoad = nsClientV3Plugin.isFirstLoad(NsClient.Collection.TREATMENTS)
                val lastLoaded =
                    if (isFirstLoad) max(nsClientV3Plugin.firstLoadContinueTimestamp.collections.treatments, dateUtil.now() - nsClientV3Plugin.maxAge)
                    else max(nsClientV3Plugin.lastLoadedSrvModified.collections.treatments, dateUtil.now() - nsClientV3Plugin.maxAge)
                if ((nsClientV3Plugin.newestDataOnServer?.collections?.treatments ?: Long.MAX_VALUE) > lastLoaded) {
                    val treatments: List<NSTreatment>
                    val response: NSAndroidClient.ReadResponse<List<NSTreatment>>?
                    if (isFirstLoad) {
                        val lastLoadedIso = dateUtil.toISOString(lastLoaded)
                        response = nsAndroidClient.getTreatmentsNewerThan(lastLoadedIso, NSClientV3Plugin.RECORDS_TO_LOAD)
                    } else {
                        response = nsAndroidClient.getTreatmentsModifiedSince(lastLoaded, NSClientV3Plugin.RECORDS_TO_LOAD)
                        aapsLogger.debug(LTag.NSCLIENT, "lastLoadedSrvModified: ${response.lastServerModified}")
                        response.lastServerModified?.let { nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = it }
                        nsClientV3Plugin.storeLastLoadedSrvModified()
                    }
                    treatments = response.values
                    aapsLogger.debug(LTag.NSCLIENT, "TREATMENTS: $treatments")
                    if (treatments.isNotEmpty()) {
                        val action = if (isFirstLoad) "RCV-F" else "RCV"
                        rxBus.send(EventNSClientNewLog("◄ $action", "${treatments.size} TRs from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                        // Schedule processing of fetched data and continue of loading
                        continueLoading = !(treatments.size != NSClientV3Plugin.RECORDS_TO_LOAD || response.code == 304)
                        nsIncomingDataProcessor.processTreatments(response.values)
                    } else {
                        // End first load
                        if (isFirstLoad) {
                            nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = lastLoaded
                            nsClientV3Plugin.storeLastLoadedSrvModified()
                        }
                        rxBus.send(EventNSClientNewLog("◄ RCV TR END", "No data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                        continueLoading = false
                    }
                } else {
                    // End first load
                    if (isFirstLoad) {
                        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = lastLoaded
                        nsClientV3Plugin.storeLastLoadedSrvModified()
                    }
                    rxBus.send(EventNSClientNewLog("◄ RCV TR END", "No new data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                    continueLoading = false
                }
            }
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            rxBus.send(EventNSClientNewLog("◄ ERROR", error.localizedMessage))
            nsClientV3Plugin.lastOperationError = error.localizedMessage
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }

        storeDataForDb.storeTreatmentsToDb()
        nsClientV3Plugin.lastOperationError = null
        return Result.success()
    }
}