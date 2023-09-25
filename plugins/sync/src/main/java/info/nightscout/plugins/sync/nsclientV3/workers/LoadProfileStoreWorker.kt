package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.utils.JsonHelper
import app.aaps.core.utils.receivers.DataWorkerStorage
import info.nightscout.plugins.sync.nsShared.NsIncomingDataProcessor
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
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
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor

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
                    rxBus.send(EventNSClientNewLog("◄ RCV", "1 PROFILE from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                    nsIncomingDataProcessor.processProfile(profile)
                } else {
                    rxBus.send(EventNSClientNewLog("◄ RCV PROFILE END", "No new data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
                }
            } else {
                rxBus.send(EventNSClientNewLog("◄ RCV PROFILE END", "No data from ${dateUtil.dateAndTimeAndSecondsString(lastLoaded)}"))
            }
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            rxBus.send(EventNSClientNewLog("◄ ERROR", error.localizedMessage))
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }

        return Result.success()
    }

}