package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.treatment.NSTreatment
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlin.math.max

@HiltWorker
class LoadTreatmentsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val nsClientV3Plugin: NSClientV3Plugin,
    private val dateUtil: DateUtil,
    private val storeDataForDb: StoreDataForDb,
    private val nsIncomingDataProcessor: NsIncomingDataProcessor,
    private val nsClientRepository: NSClientRepository
) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

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
                        nsClientRepository.addLog("◄ $action", "${treatments.size} TRs from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
                        // Schedule processing of fetched data and continue of loading
                        continueLoading =
                            response.code != 304 && nsIncomingDataProcessor.processTreatments(response.values, nsClientV3Plugin.doingFullSync)
                    } else {
                        // End first load
                        if (isFirstLoad) {
                            nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = lastLoaded
                            nsClientV3Plugin.storeLastLoadedSrvModified()
                        }
                        nsClientRepository.addLog("◄ RCV TR END", "No data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
                        continueLoading = false
                    }
                } else {
                    // End first load
                    if (isFirstLoad) {
                        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = lastLoaded
                        nsClientV3Plugin.storeLastLoadedSrvModified()
                    }
                    nsClientRepository.addLog("◄ RCV TR END", "No new data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
                    continueLoading = false
                }
            }
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.localizedMessage)
            nsClientV3Plugin.lastOperationError = error.localizedMessage
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }

        storeDataForDb.storeTreatmentsToDb(fullSync = nsClientV3Plugin.doingFullSync)
        nsClientV3Plugin.lastOperationError = null
        return Result.success()
    }
}