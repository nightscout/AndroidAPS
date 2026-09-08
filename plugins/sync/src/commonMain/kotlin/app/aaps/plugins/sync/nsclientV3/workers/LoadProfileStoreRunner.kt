package app.aaps.plugins.sync.nsclientV3.workers

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil

import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.utils.safeGetLongAllowNull
import app.aaps.core.utils.safeGetStringAllowNull
import app.aaps.core.objects.workflow.WorkOutcome

import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import dev.zacsweers.metro.Inject

import kotlin.math.max

import kotlinx.serialization.json.JsonObject

@Inject
class LoadProfileStoreRunner(

    private val aapsLogger: AAPSLogger,

    private val nsClientV3Plugin: NSClientV3Plugin,
    private val dateUtil: DateUtil,
    private val nsIncomingDataProcessor: NsIncomingDataProcessor,
    private val nsClientRepository: NSClientRepository
) {

    suspend fun run(): WorkOutcome {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return WorkOutcome.Failure("AndroidClient is null")

        try {
            val isFirstLoad = nsClientV3Plugin.isFirstLoad(NsClient.Collection.PROFILE)
            val lastLoaded = max(nsClientV3Plugin.lastLoadedSrvModified.collections.profile, dateUtil.now() - nsClientV3Plugin.maxAge)
            if ((nsClientV3Plugin.newestDataOnServer?.collections?.profile ?: Long.MAX_VALUE) > lastLoaded) {
                val response: NSAndroidClient.ReadResponse<List<JsonObject>> =
                    if (isFirstLoad) nsAndroidClient.getLastProfileStore()
                    else nsAndroidClient.getProfileModifiedSince(lastLoaded)
                val profiles = response.values
                if (profiles.isNotEmpty()) {
                    val profile = profiles[profiles.size - 1]
                    // if srvModified found in response
                    aapsLogger.debug(LTag.NSCLIENT, "lastLoadedSrvModified: ${response.lastServerModified}")
                    response.lastServerModified?.let { nsClientV3Plugin.lastLoadedSrvModified.collections.profile = it } ?:
                    // if srvModified found in record
                    profile.safeGetLongAllowNull("srvModified")?.let { nsClientV3Plugin.lastLoadedSrvModified.collections.profile = it } ?:
                    // if created_at found in record
                    profile.safeGetStringAllowNull("created_at", null)?.let { nsClientV3Plugin.lastLoadedSrvModified.collections.profile = dateUtil.fromISODateString(it) } ?:
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
            nsClientRepository.addLog("◄ ERROR", error.message)
            return WorkOutcome.Failure(error.message ?: "error")
        }

        return WorkOutcome.Success
    }

}
