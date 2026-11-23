package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.mapper.toCalibrationMbg
import app.aaps.core.nssdk.mapper.toNSDeviceStatus
import app.aaps.core.nssdk.mapper.toNSFood
import app.aaps.core.nssdk.mapper.toNSSgvV3
import app.aaps.core.nssdk.mapper.toNSTreatment
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlPublisher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientKeyExchange
import app.aaps.plugins.sync.nsclientV3.clientcontrol.MasterDocVerifier
import app.aaps.plugins.sync.nsclientV3.clientcontrol.OrphanDetector
import app.aaps.plugins.sync.nsclientV3.data.NSDeviceStatusHandler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * What to do with a frame the Nightscout websocket delivered. One implementation, every platform.
 *
 * This used to exist twice: once in `NSClientV3Service` parsing with `org.json`, once in
 * [SocketNsConnection] parsing with kotlinx. The duplication was not free. `settings` was added to
 * the subscribe list of both and given a branch in only one, so client control - pairing included -
 * was silently dead on iOS and desktop. None of it failed loudly, because a `when` with no matching
 * branch is not an error.
 *
 * Nightscout's alarms and announcements on its alarm socket are not handled any more (`_docs/ALARMS.md`,
 * step 3): every phone has its own BG alarms, and an announcement arrives as a treatment.
 *
 * The split that remains is the one that is real: **who owns the socket**. Android keeps it in a
 * bound service holding a wake lock so it survives doze and backgrounding, which is why its
 * websocket recovers where iOS may not. That is a lifecycle concern and stays per platform. What
 * happens to a frame once it arrives is not, and lives here.
 *
 * Both callers keep their handler methods as one-line delegates, so the characterization tests that
 * were written against each of them still run - `NSClientV3ServiceHandlersTest` against Android's
 * behaviour and `SocketNsConnectionHandlersTest` against the shared one. Two suites, one
 * implementation: if this drifts from what either platform used to do, one of them fails.
 *
 * Payloads arrive as JSON text ([NsSocket] hands them over that way on every platform), so nothing
 * here is Android-specific and nothing needs `org.json`.
 */
@SingleIn(AppScope::class)
@Inject
class NsFrameHandler(
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    // Deferred: the plugin owns the connection that owns this, the processor reaches the plugin as
    // its `NsClient`, and the client-control pair reach the plugin too. Nothing is looked up while
    // the graph is built - a frame arrives long afterwards.
    private val nsClientV3Plugin: () -> NSClientV3Plugin,
    private val nsIncomingDataProcessor: () -> NsIncomingDataProcessor,
    private val runningConfiguration: () -> RunningConfiguration,
    private val orphanDetector: () -> OrphanDetector,
    private val clientKeyExchange: () -> ClientKeyExchange,
    private val masterDocVerifier: () -> MasterDocVerifier,
    private val storeDataForDb: StoreDataForDb,
    private val nsClientRepository: NSClientRepository,
    private val nsDeviceStatusHandler: NSDeviceStatusHandler,
    private val appScope: CoroutineScope
) {

    fun onDataCreateUpdate(raw: String) {
        val response = parse(raw) ?: return drop("create/update", raw)
        val collection = response.str("colName") ?: return drop("create/update", raw)
        val doc = NsWsPayload.document(response) ?: return drop("create/update", raw)
        val docString = doc.toString()
        // Identity on the default log. A device status frame carries the whole APS result twice, so the
        // full frame (several KB, every few minutes) goes only to NSCLIENT_SYNC, which is off by default.
        aapsLogger.debug(LTag.NSCLIENT, "onDataCreateUpdate: $collection identifier=${doc.str("identifier")} srvModified=${doc.long("srvModified")}")
        aapsLogger.debug(LTag.NSCLIENT_SYNC) { "onDataCreateUpdate: $response" }
        nsClientRepository.addLog("◄ WS CREATE/UPDATE", collection, doc)

        // Mandatory in a Nightscout v3 document. Defaulting it to 0 invents a value for a field that
        // cannot legitimately be absent, and 0 is not inert: it would be written to the high-water
        // mark, and `OrphanDetector.onSettingsDoc` reads 0 as "no timestamp, skip the race guard",
        // which is how one malformed frame could declare a freshly paired client an orphan.
        val srvModified = doc.long("srvModified") ?: return drop(collection, raw)

        // The high-water mark must not move until the catch-up round after a (re)connect has
        // finished, or the next load asks for "modified since (just moved pointer)" and skips the
        // very window it should backfill.
        if (nsClientV3Plugin().initialLoadFinished) {
            nsClientV3Plugin().lastLoadedSrvModified.set(collection, srvModified)
            nsClientV3Plugin().storeLastLoadedSrvModified()
        }

        // advanceInitialLoadCursor = false below, for the same reason as the guard above: a live
        // record must not move the first-load cursor either. It carries the time of "now", so
        // during a first load it would make the REST load start at now and the history behind it -
        // profile switches included - would never be read.
        when (collection) {
            "devicestatus" -> decode(collection, raw) { docString.toNSDeviceStatus() }?.let {
                nsDeviceStatusHandler.handleNewData(arrayOf(it), live = true)
            }

            "entries"      -> {
                decode(collection, raw) { docString.toNSSgvV3() }?.let {
                    nsIncomingDataProcessor().processSgvs(listOf(it), doFullSync = false, advanceInitialLoadCursor = false)
                    storeDataForDb.requestStoreGlucoseValues()
                }
                // The same collection also carries AAPS calibration entries.
                decode(collection, raw) { docString.toCalibrationMbg() }?.let {
                    nsIncomingDataProcessor().processCalibrations(listOf(it), doFullSync = false)
                    storeDataForDb.requestStoreCalibrationEntries()
                }
            }

            "profile"      -> appScope.launch { nsIncomingDataProcessor().processProfile(doc, doFullSync = false) }

            "treatments"   -> decode(collection, raw) { docString.toNSTreatment() }?.let {
                nsIncomingDataProcessor().processTreatments(listOf(it), doFullSync = false, advanceInitialLoadCursor = false)
                storeDataForDb.requestStoreTreatments(fullSync = false)
            }

            "foods"        -> decode(collection, raw) { docString.toNSFood() }?.let {
                nsIncomingDataProcessor().processFood(listOf(it))
                storeDataForDb.requestStoreFoods()
            }

            "settings"     -> onSettings(doc, docString, srvModified)
        }
    }

    /** Client control travels here. Without it, pairing and every command after it are inert. */
    private fun onSettings(doc: JsonObject, docString: String, srvModified: Long) {
        val identifier = doc.str("identifier") ?: ""
        when {
            // Client: cold config doc - apply everything except the active scene. Only when the master signed it
            // (MasterDocVerifier): a client token may write to settings too.
            config.AAPSCLIENT && identifier == SettingsIdentifiers.COLD                                   ->
                masterDocVerifier().open(identifier, doc)?.let { opened ->
                    runningConfiguration().applyCold(opened.configuration)
                    // Only the orphan bookkeeping is deferred, because onSettingsDoc takes the
                    // repository mutex and this runs on the socket's thread, not in a coroutine.
                    // applyCold and the liveness clock stay inline.
                    appScope.launch { orphanDetector().onSettingsDoc(opened.configuration, srvModified) }
                    // A live config push proves the master is alive - at the time it signed it, so a doc put back
                    // later by somebody else does not make a master that is gone look alive.
                    nsClientV3Plugin().bumpMasterSignal(opened.signedAt ?: srvModified)
                }

            // Client: hot state doc - the active scene only. Kept apart from the
            // cold branch so this can never clear a running scene.
            config.AAPSCLIENT && identifier == SettingsIdentifiers.STATE                                  ->
                masterDocVerifier().open(identifier, doc)?.let { opened ->
                    runningConfiguration().applyHot(opened.configuration)
                    nsClientV3Plugin().bumpMasterSignal(opened.signedAt ?: srvModified)
                }

            // Client: master->client command ACK. Must be tested BEFORE the generic IDENTIFIER_PREFIX
            // branch, because ack identifiers carry that prefix too and the master receiver would
            // otherwise try to verify an ack as an inbound command.
            config.AAPSCLIENT && identifier.startsWith(ClientControlPublisher.IDENTIFIER_ACK_PREFIX)      ->
                appScope.launch { nsClientV3Plugin().handleClientControlAckEvent(doc) }

            // Client: master->client live bolus-progress mirror. Same ordering rule as the ACK.
            config.AAPSCLIENT && identifier.startsWith(ClientControlPublisher.IDENTIFIER_PROGRESS_PREFIX) ->
                appScope.launch { nsClientV3Plugin().handleClientControlProgressEvent(doc) }

            // Client: the master's answer to the key exchange in the hello. Same ordering rule as the ACK.
            config.AAPSCLIENT && identifier.startsWith(ClientControlPublisher.IDENTIFIER_KEY_PREFIX)      ->
                appScope.launch {
                    runCatching { clientKeyExchange().onKeyDoc(doc) }
                        .onFailure { aapsLogger.error(LTag.NSCLIENT, "ClientControl key exchange failed: ${it.message}", it) }
                }

            // Master: inbound client-control envelopes. The plugin gates on the master toggle itself.
            // Guarded by !AAPSCLIENT because Nightscout echoes every write back to its sender, and a
            // client must not process its own outgoing command (an unknown clientId leads to
            // deleteSettings and an HTTP 410 tombstone).
            !config.AAPSCLIENT && identifier.startsWith(ClientControlPublisher.IDENTIFIER_PREFIX)         ->
                appScope.launch { nsClientV3Plugin().handleClientControlSettingsEvent(identifier, doc) }
        }
    }

    fun onDataDelete(raw: String) {
        val response = parse(raw) ?: return drop("delete", raw)
        aapsLogger.debug(LTag.NSCLIENT, "onDataDelete: $response")
        // A document with no collection name matches no branch below and deletes nothing, which is
        // what Android did too - there `optString` returned "" rather than null.
        val collection = response.str("colName") ?: ""
        val identifier = response.str("identifier") ?: ""
        nsClientRepository.addLog("◄ WS DELETE", "$collection $identifier")
        when (collection) {
            "treatments" -> {
                storeDataForDb.addToDeleteTreatment(identifier)
                storeDataForDb.requestUpdateDeletedTreatments()
            }

            // Only when BG is taken from Nightscout at all: otherwise anyone who can write there could remove
            // this phone's readings.
            "entries"    -> if (nsIncomingDataProcessor().acceptsGlucose(doFullSync = false)) {
                storeDataForDb.addToDeleteGlucoseValue(identifier)
                storeDataForDb.requestUpdateDeletedGlucoseValues()
            }
        }
    }

    /**
     * A frame that cannot be read is dropped with a line saying so.
     *
     * Android used to throw here instead - `JSONObject(raw)` and `getLong` both do - out of a bare
     * socket.io listener, which is neither visible nor safe. Silence would be no better: a stream of
     * unreadable frames must not look the same as no frames at all.
     */
    private fun drop(what: String, raw: String, reason: String? = null) {
        val because = reason?.let { " ($it)" } ?: ""
        aapsLogger.error(LTag.NSCLIENT, "Dropping unreadable $what frame$because: ${raw.take(200)}")
    }

    /**
     * Turns a document into its model, or drops the frame when that fails.
     *
     * Any uploader can write to Nightscout, so a document in a shape this version does not expect is
     * normal, not a fault in AAPS. Thrown here, it escaped the socket.io listener and crashed the app,
     * and again for every such document: a device status with `"uploader":"GG-Host"` did that every
     * few minutes on 4.0.0-dev-e. Only the decoding is guarded. A failure while processing a document
     * that did decode is a real fault and still surfaces.
     */
    private inline fun <T> decode(collection: String, raw: String, read: () -> T): T? =
        try {
            read()
        } catch (e: Exception) {
            drop(collection, raw, e.message)
            null
        }

    // Frame reading lives in NsWsPayload, which is tested on its own.
    private fun parse(raw: String): JsonObject? = NsWsPayload.parse(raw)
    private fun JsonObject.str(key: String): String? = NsWsPayload.string(this, key)
    private fun JsonObject.long(key: String): Long? = NsWsPayload.long(this, key)
}
