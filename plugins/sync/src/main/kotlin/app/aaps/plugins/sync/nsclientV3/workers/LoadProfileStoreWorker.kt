package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.JsonHelper
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.max

class LoadProfileStoreWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var nsClientRepository: NSClientRepository

    override suspend fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))

        try {
            val isFirstLoad = nsClientV3Plugin.isFirstLoad(NsClient.Collection.PROFILE)
            val lastLoaded = max(nsClientV3Plugin.lastLoadedSrvModified.collections.profile, dateUtil.now() - nsClientV3Plugin.maxAge)
            if ((nsClientV3Plugin.newestDataOnServer?.collections?.profile ?: Long.MAX_VALUE) > lastLoaded) {
                val response: NSAndroidClient.ReadResponse<List<JSONObject>> =
                    if (isFirstLoad) nsAndroidClient.getLastProfileStore()
                    else nsAndroidClient.getProfileModifiedSince(lastLoaded)
                val profiles = response.values
                if (profiles.isNotEmpty()) {
                    val profile = profiles[profiles.size - 1]
                    // if srvModified found in response
                    aapsLogger.debug(LTag.NSCLIENT, "lastLoadedSrvModified: ${response.lastServerModified}")
                    response.lastServerModified?.let { nsClientV3Plugin.lastLoadedSrvModified.collections.profile = it } ?:
                    // if srvModified found in record
                    JsonHelper.safeGetLongAllowNull(profile, "srvModified")?.let { nsClientV3Plugin.lastLoadedSrvModified.collections.profile = it } ?:
                    // if created_at found in record
                    JsonHelper.safeGetStringAllowNull(profile, "created_at", null)?.let { nsClientV3Plugin.lastLoadedSrvModified.collections.profile = dateUtil.fromISODateString(it) } ?:
                    // if not found reset to now
                    { nsClientV3Plugin.lastLoadedSrvModified.collections.profile = dateUtil.now() }
                    nsClientV3Plugin.storeLastLoadedSrvModified()
                    aapsLogger.debug(LTag.NSCLIENT, "PROFILE: $profile")
                    nsClientRepository.addLog("◄ RCV", "1 PROFILE from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
                    nsIncomingDataProcessor.processProfile(profile, nsClientV3Plugin.doingFullSync)
                } else {
                    nsClientRepository.addLog("◄ RCV PROFILE END", "No new data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
                }
            } else {
                nsClientRepository.addLog("◄ RCV PROFILE END", "No data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}")
            }
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.localizedMessage)
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }

        return Result.success()
    }

}