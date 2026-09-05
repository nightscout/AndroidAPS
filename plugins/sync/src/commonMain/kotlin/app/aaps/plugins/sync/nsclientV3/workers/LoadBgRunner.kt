package app.aaps.plugins.sync.nsclientV3.workers

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import dev.zacsweers.metro.Inject
import kotlin.math.max

/**
 * Fetches glucose values (and the calibration entries that ride the same cursor) from Nightscout.
 *
 * The body of `LoadBgWorker`, split out so it is plain Kotlin. Nothing here knows about WorkManager;
 * the worker is a shim that maps [WorkOutcome] onto a `ListenableWorker.Result`.
 */
@Inject
class LoadBgRunner(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val nsClientV3Plugin: NSClientV3Plugin,
    private val nsClientSource: NSClientSource,
    private val nsIncomingDataProcessor: NsIncomingDataProcessor,
    private val storeDataForDb: StoreDataForDb,
    private val nsClientRepository: NSClientRepository
) {

    suspend fun run(): WorkOutcome {
        if (!nsClientSource.isEnabled() && !preferences.get(BooleanKey.NsClientAcceptCgmData) && !nsClientV3Plugin.doingFullSync)
            return WorkOutcome.Skipped("Load not enabled")

        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return WorkOutcome.Failure("AndroidClient is null")
        var continueLoading = true
        try {
            while (continueLoading) {
                val isFirstLoad = nsClientV3Plugin.isFirstLoad(NsClient.Collection.ENTRIES)
                val lastLoaded =
                    if (isFirstLoad) max(nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries, dateUtil.now() - nsClientV3Plugin.maxAge)
                    else max(nsClientV3Plugin.lastLoadedSrvModified.collections.entries, dateUtil.now() - nsClientV3Plugin.maxAge)
                if ((nsClientV3Plugin.newestDataOnServer?.collections?.entries ?: Long.MAX_VALUE) > lastLoaded) {
                    val sgvs: List<NSSgvV3>
                    val response: NSAndroidClient.ReadResponse<List<NSSgvV3>>?
                    // Replaces the old `response.code != 304` brake.
                    //
                    // A 304 could only ever appear because OkHttp's disk cache revalidated a GET, and
                    // that cache is gone with Retrofit. What the 304 actually meant is tested directly
                    // now: the server has nothing newer, so the cursor cannot move and another page
                    // would return the same rows for ever.
                    //
                    // Only the modified-since path can stall like this. The first load pages by date
                    // and always moves forward, and its response carries no server timestamp at all.
                    var cursorStalled = false
                    if (isFirstLoad) response = nsAndroidClient.getSgvsNewerThan(lastLoaded, NSClientV3Plugin.RECORDS_TO_LOAD)
                    else {
                        response = nsAndroidClient.getSgvsModifiedSince(lastLoaded, NSClientV3Plugin.RECORDS_TO_LOAD)
                        aapsLogger.debug(LTag.NSCLIENT, "lastLoadedSrvModified: ${response.lastServerModified}")
                        cursorStalled = response.lastServerModified?.let { it <= lastLoaded } == true
                        response.lastServerModified?.let { nsClientV3Plugin.lastLoadedSrvModified.collections.entries = it }
                        nsClientV3Plugin.storeLastLoadedSrvModified()
                        nsClientV3Plugin.scheduleIrregularExecution() // Idea is to run after 5 min after last BG
                    }
                    sgvs = response.values
                    // Calibration mbg entries ride the same entries fetch + cursor; ingest them
                    // regardless of whether there were any sgvs in this page.
                    if (response.calibrations.isNotEmpty()) {
                        nsClientRepository.addLog("◄ RCV", "${response.calibrations.size} calibrations from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
                        nsIncomingDataProcessor.processCalibrations(response.calibrations, nsClientV3Plugin.doingFullSync)
                    }
                    aapsLogger.debug(LTag.NSCLIENT, "SGVS: $sgvs")
                    if (sgvs.isNotEmpty()) {
                        val action = if (isFirstLoad) "RCV-F" else "RCV"
                        nsClientRepository.addLog("◄ $action", "${sgvs.size} SVGs from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
                        // Schedule processing of fetched data and continue of loading
                        continueLoading = !cursorStalled && nsIncomingDataProcessor.processSgvs(sgvs, nsClientV3Plugin.doingFullSync)
                    } else {
                        // End first load
                        if (isFirstLoad) {
                            nsClientV3Plugin.lastLoadedSrvModified.collections.entries = lastLoaded
                            nsClientV3Plugin.storeLastLoadedSrvModified()
                        }
                        nsClientRepository.addLog("◄ RCV BG END", "No data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
                        continueLoading = false
                    }
                } else {
                    // End first load
                    if (isFirstLoad) {
                        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = lastLoaded
                        nsClientV3Plugin.storeLastLoadedSrvModified()
                    }
                    nsClientRepository.addLog("◄ RCV BG END", "No new data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
                    continueLoading = false
                }
            }
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.message)
            nsClientV3Plugin.lastOperationError = error.message
            return WorkOutcome.Failure(error.message ?: "error")
        }

        storeDataForDb.storeGlucoseValuesToDb()
        storeDataForDb.storeCalibrationEntriesToDb()
        nsClientV3Plugin.lastOperationError = null
        return WorkOutcome.Success
    }
}
