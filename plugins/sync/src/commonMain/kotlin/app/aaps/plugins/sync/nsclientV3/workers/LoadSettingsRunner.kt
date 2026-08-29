package app.aaps.plugins.sync.nsclientV3.workers

import app.aaps.core.data.json.OrgJsonCompat.optLongCompat
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.utils.DateUtil

import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.localmodel.configuration.NSRunningConfiguration
import app.aaps.core.objects.workflow.WorkOutcome

import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers
import app.aaps.plugins.sync.nsclientV3.clientcontrol.OrphanDetector
import app.aaps.plugins.sync.nsclientV3.extensions.toRunningConfiguration
import dev.zacsweers.metro.Inject

/**
 * Catch-up loader for the NS `settings/aaps` document.
 *
 * Master role (`config.APS`): no-op (master publishes via [RunningConfigurationPublisher],
 * applying our own doc back to ourselves would be a feedback loop).
 *
 * Client role (`config.AAPSCLIENT`): GET the doc, extract `runningConfig`, apply it via
 * [RunningConfiguration.apply], advance the settings high-water-mark. Real-time updates
 * after this initial catch-up arrive via the WS `settings` branch in `NSClientV3Service`.
 */
@Inject
class LoadSettingsRunner(

    private val aapsLogger: AAPSLogger,

    private val nsClientV3Plugin: NSClientV3Plugin,
    private val nsClientRepository: NSClientRepository,
    private val runningConfiguration: RunningConfiguration,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val orphanDetector: OrphanDetector
) {

    suspend fun run(): WorkOutcome {
        val client = nsClientV3Plugin.nsAndroidClient ?: return WorkOutcome.Success
        if (!config.AAPSCLIENT) return WorkOutcome.Success // master skips reading

        try {
            // Cold doc first: scene definitions (in the cold doc) must exist before the hot doc's
            // activeScene — which references a sceneId — is applied.
            load(client, SettingsIdentifiers.COLD) { cfg, srvModified ->
                runningConfiguration.applyCold(cfg)
                orphanDetector.onSettingsDoc(cfg, srvModified ?: 0L)
                // The LastModified model has a single `settings` slot — the cold doc owns it.
                // (Also satisfies isFirstLoad(SETTINGS), which checks collections.settings != 0.)
                // The hot doc is fetched unconditionally below, so it needs no pointer of its own.
                srvModified?.let {
                    nsClientV3Plugin.lastLoadedSrvModified.set("settings", it)
                    nsClientV3Plugin.storeLastLoadedSrvModified()
                }
            }
            load(client, SettingsIdentifiers.STATE) { cfg, _ ->
                runningConfiguration.applyHot(cfg)
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "LoadSettingsWorker failed", e)
            nsClientRepository.addLog("◄ ERROR SETTINGS", e.message ?: e.toString())
            nsClientV3Plugin.lastOperationError = e.message
            return WorkOutcome.Failure(e.message ?: "error")
        }
        nsClientV3Plugin.lastOperationError = null
        return WorkOutcome.Success
    }

    private suspend fun load(
        client: NSAndroidClient,
        identifier: String,
        // suspend: the apply block reaches OrphanDetector, which takes the repository mutex now.
        apply: suspend (NSRunningConfiguration, Long?) -> Unit
    ) {
        val response = client.getSettings(identifier)
        val doc = response.values
        if (doc == null) {
            nsClientRepository.addLog("◄ RCV SETTINGS", "$identifier: no doc yet")
            return
        }
        // ETag isn't set for settings GETs, but srvModified is in the doc body
        val srvModified = doc.optLongCompat("srvModified", 0L).takeIf { it > 0 } ?: response.lastServerModified
        doc.toString().toRunningConfiguration()?.let { configuration ->
            apply(configuration, srvModified)
            val ts = srvModified?.let { dateUtil.dateAndTimeAndSecondsString(it) } ?: "?"
            nsClientRepository.addLog("◄ SETTINGS", "$identifier applied srvModified=$ts")
        } ?: nsClientRepository.addLog("◄ SETTINGS", "$identifier present but missing/invalid runningConfig")
    }
}
