package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.mapper.toCalibrationMbg
import app.aaps.core.nssdk.mapper.toNSDeviceStatus
import app.aaps.core.nssdk.mapper.toNSFood
import app.aaps.core.nssdk.mapper.toNSSgvV3
import app.aaps.core.nssdk.mapper.toNSTreatment
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.sync.nsclientV3.NSAlarmObject
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlPublisher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.OrphanDetector
import app.aaps.plugins.sync.nsclientV3.data.NSDeviceStatusHandler
import app.aaps.plugins.sync.nsclientV3.extensions.toRunningConfiguration
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
 * was silently dead on iOS and desktop; the per-level alarm snooze was honoured on Android and
 * ignored elsewhere; and the snooze buttons that write that preference existed only on Android, so
 * the two halves could never meet. None of it failed loudly, because a `when` with no matching
 * branch is not an error.
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
class NsFrameHandler @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val config: Config,
    // Deferred: the plugin owns the connection that owns this, the processor reaches the plugin as
    // its `NsClient`, and the client-control pair reach the plugin too. Nothing is looked up while
    // the graph is built - a frame arrives long afterwards.
    private val nsClientV3Plugin: () -> NSClientV3Plugin,
    private val nsIncomingDataProcessor: () -> NsIncomingDataProcessor,
    private val runningConfiguration: () -> RunningConfiguration,
    private val orphanDetector: () -> OrphanDetector,
    private val storeDataForDb: StoreDataForDb,
    private val notificationManager: NotificationManager,
    private val nsClientRepository: NSClientRepository,
    private val nsDeviceStatusHandler: NSDeviceStatusHandler,
    private val dateUtil: DateUtil,
    private val appScope: CoroutineScope
) {

    fun onDataCreateUpdate(raw: String) {
        val response = parse(raw) ?: return drop("create/update", raw)
        val collection = response.str("colName") ?: return drop("create/update", raw)
        val doc = NsWsPayload.document(response) ?: return drop("create/update", raw)
        val docString = doc.toString()
        aapsLogger.debug(LTag.NSCLIENT, "onDataCreateUpdate: $response")
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

        when (collection) {
            "devicestatus" -> nsDeviceStatusHandler.handleNewData(arrayOf(docString.toNSDeviceStatus()), live = true)

            "entries"      -> {
                docString.toNSSgvV3()?.let {
                    nsIncomingDataProcessor().processSgvs(listOf(it), doFullSync = false)
                    storeDataForDb.requestStoreGlucoseValues()
                }
                // The same collection also carries AAPS calibration entries.
                docString.toCalibrationMbg()?.let {
                    nsIncomingDataProcessor().processCalibrations(listOf(it), doFullSync = false)
                    storeDataForDb.requestStoreCalibrationEntries()
                }
            }

            "profile"      -> appScope.launch { nsIncomingDataProcessor().processProfile(doc, doFullSync = false) }

            "treatments"   -> docString.toNSTreatment()?.let {
                nsIncomingDataProcessor().processTreatments(listOf(it), doFullSync = false)
                storeDataForDb.requestStoreTreatments(fullSync = false)
            }

            "foods"        -> docString.toNSFood()?.let {
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
            // Client: cold config doc - apply everything except the active scene.
            config.AAPSCLIENT && identifier == SettingsIdentifiers.COLD                                   ->
                docString.toRunningConfiguration()?.let {
                    runningConfiguration().applyCold(it)
                    // Only the orphan bookkeeping is deferred, because onSettingsDoc takes the
                    // repository mutex and this runs on the socket's thread, not in a coroutine.
                    // applyCold and the liveness clock stay inline.
                    appScope.launch { orphanDetector().onSettingsDoc(it, srvModified) }
                    // A live config push proves the master is alive now - feed the liveness clock.
                    nsClientV3Plugin().bumpMasterSignal(srvModified)
                }

            // Client: hot state doc - the active scene and runtime flags only. Kept apart from the
            // cold branch so this can never clear a running scene.
            config.AAPSCLIENT && identifier == SettingsIdentifiers.STATE                                  ->
                docString.toRunningConfiguration()?.let {
                    runningConfiguration().applyHot(it)
                    nsClientV3Plugin().bumpMasterSignal(srvModified)
                }

            // Client: master->client command ACK. Must be tested BEFORE the generic IDENTIFIER_PREFIX
            // branch, because ack identifiers carry that prefix too and the master receiver would
            // otherwise try to verify an ack as an inbound command.
            config.AAPSCLIENT && identifier.startsWith(ClientControlPublisher.IDENTIFIER_ACK_PREFIX)      ->
                appScope.launch { nsClientV3Plugin().handleClientControlAckEvent(doc) }

            // Client: master->client live bolus-progress mirror. Same ordering rule as the ACK.
            config.AAPSCLIENT && identifier.startsWith(ClientControlPublisher.IDENTIFIER_PROGRESS_PREFIX) ->
                appScope.launch { nsClientV3Plugin().handleClientControlProgressEvent(doc) }

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

            "entries"    -> {
                storeDataForDb.addToDeleteGlucoseValue(identifier)
                storeDataForDb.requestUpdateDeletedGlucoseValues()
            }
        }
    }

    fun onAnnouncement(raw: String) {
        val data = parse(raw) ?: return drop("announcement", raw)
        aapsLogger.debug(LTag.NSCLIENT, data.toString())
        nsClientRepository.addLog("◄ ANNOUNCEMENT", data.str("message") ?: "")
        // No snooze check here, matching Android: an announcement is not an alarm.
        if (preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)) post(NSAlarmObject(data))
    }

    fun onAlarm(raw: String) = alarm(raw, "◄ ALARM")

    /** Same gate and same handling as [onAlarm]; kept apart so the log says which one fired. */
    fun onUrgentAlarm(raw: String) = alarm(raw, "◄ URGENT ALARM")

    private fun alarm(raw: String, logLabel: String) {
        val data = parse(raw) ?: return drop("alarm", raw)
        aapsLogger.debug(LTag.NSCLIENT, data.toString())
        // The message body, not the title: it carries the BG/IOB/COB text a user is asked to
        // screenshot when reporting an alarm.
        nsClientRepository.addLog(logLabel, data.str("message") ?: "")
        if (!preferences.get(BooleanKey.NsClientNotificationsFromAlarms)) return
        // The per-level snooze. Without it an alarm the user explicitly silenced comes back on the
        // next push, which is worse than not showing it: an alarm that ignores its own snooze
        // teaches people to ignore the alarm. Composed with the level, so snoozing a warning does
        // not silence an urgent one. A missing key reads as 0, meaning never snoozed.
        val snoozedTo = preferences.get(LongComposedKey.NotificationSnoozedTo, data.str("level") ?: "")
        if (snoozedTo == 0L || dateUtil.now() > snoozedTo) post(NSAlarmObject(data))
    }

    fun onClearAlarm(raw: String) {
        val data = parse(raw) ?: return drop("clear alarm", raw)
        aapsLogger.debug(LTag.NSCLIENT, data.toString())
        nsClientRepository.addLog("◄ CLEARALARM", data.str("title") ?: "")
        notificationManager.dismiss(NotificationId.NS_ALARM)
        notificationManager.dismiss(NotificationId.NS_URGENT_ALARM)
    }

    /**
     * The snooze buttons offered on an alarm notification.
     *
     * The only writer of [LongComposedKey.NotificationSnoozedTo], which [alarm] reads, and the only
     * caller of `handleClearAlarm`, which acknowledges the alarm back to Nightscout.
     */
    private fun snoozeActions(alarm: NSAlarm): List<NotificationAction> =
        listOf(15, 30, 60).map { minutes ->
            val label = when (minutes) {
                15   -> CoreUiStrings.snooze_15m
                30   -> CoreUiStrings.snooze_30m
                else -> CoreUiStrings.snooze_60m
            }
            NotificationAction(label) {
                val snoozeMs = minutes * 60 * 1000L
                nsClientV3Plugin().handleClearAlarm(alarm, snoozeMs)
                // Cascade across all levels. Nightscout cascades a level-2 ack down to level 1 but
                // keeps emitting lower-level forecast alarms that would otherwise slip past a
                // single-level snooze and re-alarm. This also makes the read above work: it looks
                // the snooze up by the frame's level.
                val snoozedUntil = dateUtil.now() + snoozeMs
                for (level in 0..2)
                    preferences.put(LongComposedKey.NotificationSnoozedTo, level.toString(), value = snoozedUntil)
            }
        }

    /** Level decides the notification. */
    private fun post(alarm: NSAlarm) {
        when (alarm.level) {
            0    -> notificationManager.post(
                id = NotificationId.NS_ANNOUNCEMENT,
                text = alarm.message,
                level = NotificationLevel.ANNOUNCEMENT,
                validMinutes = 60,
                actions = snoozeActions(alarm)
            )

            1    -> notificationManager.post(
                id = NotificationId.NS_ALARM,
                text = alarm.title,
                sound = AlarmSound.ALARM,
                actions = snoozeActions(alarm)
            )

            2    -> notificationManager.post(
                id = NotificationId.NS_URGENT_ALARM,
                text = alarm.title,
                sound = AlarmSound.URGENT_ALARM,
                actions = snoozeActions(alarm)
            )

            else -> Unit
        }
    }

    /**
     * A frame that cannot be read is dropped with a line saying so.
     *
     * Android used to throw here instead - `JSONObject(raw)` and `getLong` both do - out of a bare
     * socket.io listener, which is neither visible nor safe. Silence would be no better: a stream of
     * unreadable frames must not look the same as no frames at all.
     */
    private fun drop(what: String, raw: String) {
        aapsLogger.error(LTag.NSCLIENT, "Dropping unreadable $what frame: ${raw.take(200)}")
    }

    // Frame reading lives in NsWsPayload, which is tested on its own.
    private fun parse(raw: String): JsonObject? = NsWsPayload.parse(raw)
    private fun JsonObject.str(key: String): String? = NsWsPayload.string(this, key)
    private fun JsonObject.long(key: String): Long? = NsWsPayload.long(this, key)
}
