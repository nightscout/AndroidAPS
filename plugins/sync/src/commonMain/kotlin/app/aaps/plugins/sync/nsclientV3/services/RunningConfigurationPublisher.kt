package app.aaps.plugins.sync.nsclientV3.services

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.RunningConfigurationKeys
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.core.objects.extensions.observeChange
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers
import app.aaps.plugins.sync.nsclientV3.clientcontrol.AuthorizedClientsRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.clientRole
import app.aaps.plugins.sync.nsclientV3.services.RunningConfigurationPublisher.Companion.COLD_DEBOUNCE_MS
import app.aaps.plugins.sync.nsclientV3.services.RunningConfigurationPublisher.Companion.HOT_DEBOUNCE_MS
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile

/**
 * Master-side publisher for the NS running-config `settings` documents.
 *
 * Splits the config across two docs by write-frequency (see [SettingsIdentifiers]):
 *  - Cold ([SettingsIdentifiers.COLD]): plugin config + overview + definitions + authorized
 *    clients. Triggered by [RunningConfigurationKeys.observableKeys] changes, plugin switches
 *    ([EventConfigBuilderChange]) and authorized-client changes; debounced [COLD_DEBOUNCE_MS]. One that could not be
 *    published is tried again later (`publishColdOrRetry`).
 *  - Hot ([SettingsIdentifiers.STATE]): active scene + computed runtime flags. Triggered by
 *    [RunningConfigurationKeys.hotKeys] (scene lifecycle); debounced [HOT_DEBOUNCE_MS].
 *
 * Document shape (both): `{ schemaVersion: 1, runningConfig: <…> }`, upserted via `updateSettings`.
 *
 * Started by [NSClientV3Plugin.onStart] and stopped by `onStop`.
 * Only active when `config.APS` is true (master role); no-op on aapsclient.
 */
@OptIn(FlowPreview::class)
@SingleIn(AppScope::class)
@Inject
class RunningConfigurationPublisher(
    private val runningConfiguration: RunningConfiguration,
    private val runningConfigurationKeys: RunningConfigurationKeys,
    private val nsClientV3Plugin: () -> NSClientV3Plugin,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val nsClientRepository: NSClientRepository,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val authorizedRepository: AuthorizedClientsRepository,
) {

    private var job: Job? = null

    // The waiting retry of a cold doc that was not published, and how long the next one waits. Set from the cold
    // collector and the WS-connect collector, which can run on different threads.
    @Volatile private var coldRetry: Job? = null
    @Volatile private var coldRetryDelayMs = COLD_RETRY_FIRST_MS

    // Force a cold republish on demand. Used after the master applies an inbound client pref command so
    // the client always gets the authoritative value back — even when LWW dropped the pushed value as a
    // no-op (which wouldn't change a key and so wouldn't trigger the observeChange republish). Lets the
    // client converge a superseded edit instead of being stuck on its optimistic value.
    private val forceColdRepublish = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Master-side: republish the cold doc now (debounced with the other cold triggers).
     *
     * Why an explicit, unconditional republish is needed (rather than relying on the observeChange
     * trigger): when an inbound client pref command carries a value that loses last-write-wins — or
     * even ties it with the SAME `SyncedPrefModified` stamp — there is no winner, so the stored key
     * does not change and observeChange never fires. The client would then stay stuck on its
     * optimistic value. Forcing a republish here pushes the authoritative value back so the client
     * converges its superseded edit.
     */
    fun requestColdRepublish() {
        forceColdRepublish.tryEmit(Unit)
    }

    fun start(scope: CoroutineScope) {
        if (!config.APS) return
        if (job != null) return
        job = scope.launch {
            // Publish on connect, not blindly at plugin start (the NS connection may not be ready yet,
            // so a start-time publish would just fail silently). When WS is used, the WS-connect collector
            // below owns the initial publish (it fires on first connect, or immediately if already
            // connected). In poll mode there is no WS connect event to hook, so fall back to a start-time
            // publish there.
            if (!preferences.get(BooleanKey.NsClient3UseWs)) {
                publishColdOrRetry()
                publishHot()
            }

            val switchTrigger: Flow<Unit> = rxBus.toFlow(EventConfigBuilderChange::class).map { }
            // Authorized-clients changes (pair / unpair / revoke / markActive) republish so paired
            // clients can read the current roster and detect when they've been orphaned.
            val authorizedClientsTrigger: Flow<Unit> = authorizedRepository.observe().map { }
            val coldKeyTriggers: List<Flow<Unit>> = runningConfigurationKeys.observableKeys()
                .map { preferences.observeChange(it) }
            val hotKeyTriggers: List<Flow<Unit>> = runningConfigurationKeys.hotKeys()
                .map { preferences.observeChange(it) }

            // Cold doc: rarely-changing config. Longer debounce collapses bulk edits.
            launch {
                (coldKeyTriggers + switchTrigger + authorizedClientsTrigger + forceColdRepublish).merge()
                    .debounce(COLD_DEBOUNCE_MS)
                    .collect { publishColdOrRetry() }
            }
            // Hot doc: scene lifecycle. Short debounce so a scene shows on clients quickly.
            launch {
                hotKeyTriggers.merge()
                    .debounce(HOT_DEBOUNCE_MS)
                    .collect { publishHot() }
            }
            // Master WS connect → publish both docs. This is the INITIAL publish (fires on first
            // connect, or immediately if already connected when started) AND the reconnect republish:
            // recovers a config edit made while NS was down (putSettings has no retry) and recreates an
            // auto-pruned doc. Debounced to swallow reconnect flapping. Republishing unchanged config is
            // harmless: clients re-apply idempotently and per-key LWW no-ops (same SyncedPrefModified).
            launch {
                nsClientV3Plugin().wsConnectedFlow
                    .filter { it }
                    .debounce(WS_RECONNECT_DEBOUNCE_MS)
                    .collect {
                        publishColdOrRetry()
                        publishHot()
                    }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        coldRetry = null
        coldRetryDelayMs = COLD_RETRY_FIRST_MS
    }

    /**
     * Publishes the cold doc, or tries again later when it could not. A cold publish that is lost is not repeated by
     * anything else until the next change, and that can be much later: a new client then waited minutes for the
     * roster with its role. Each failure in a row doubles the wait, up to [COLD_RETRY_MAX_MS].
     */
    private suspend fun CoroutineScope.publishColdOrRetry() {
        coldRetry?.cancel()
        coldRetry = null
        if (publishCold()) {
            coldRetryDelayMs = COLD_RETRY_FIRST_MS
            return
        }
        val wait = coldRetryDelayMs
        coldRetryDelayMs = (wait * 2).coerceAtMost(COLD_RETRY_MAX_MS)
        aapsLogger.debug(LTag.NSCLIENT, "Running configuration not published, trying again in ${wait / 1000} s")
        coldRetry = launch {
            delay(wait)
            requestColdRepublish()
        }
    }

    // Cold doc: full plugin/overview/definition config + the authorized-clients roster.
    // Returns true if a doc was published, false if it was not (pump not initialized yet, no Nightscout client, or the
    // upload failed).
    private suspend fun publishCold(): Boolean {
        val base = runningConfiguration.configuration()
        if (base.isEmpty()) {
            aapsLogger.debug(LTag.NSCLIENT, "Running configuration empty: the pump is not initialized yet")
            return false
        }
        // Append the authorized-clients roster directly on the runningConfig doc — the
        // canonical RunningConfiguration plugin (in :plugins:configuration) cannot reach
        // AuthorizedClientsRepository without a new inter-module dependency, so we attach
        // this master-local block here instead. Only Active entries are exposed.
        val activeClients = authorizedRepository.current(dateUtil.now())
            .filter { it.state == ClientState.Active }
        // kotlinx JsonObject is immutable, so the doc is rebuilt instead of edited in place: the original
        // keys in their original order, and authorizedClients appended last. The client-control switch
        // travels in syncedPrefs like every other cold key; nothing here changes it. Hardened mode keeps
        // the WebSocket on while the switch is on, so "on" always means the master can serve commands.
        val payload = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            put("authorizedClients", buildJsonObject {
                put("clientIds", JsonArray(activeClients.map { JsonPrimitive(it.clientId) }))
                // Each client reads its own role to know which controls to show; the master enforces it anyway.
                put("roles", buildJsonObject { activeClients.forEach { put(it.clientId, it.clientRole.name) } })
            })
        }
        return putSettings(SettingsIdentifiers.COLD, payload)
    }

    // Hot doc: active scene + computed runtime flags. Small and frequently republished.
    private suspend fun publishHot() {
        putSettings(SettingsIdentifiers.STATE, runningConfiguration.activeSceneConfiguration())
    }

    /** True when Nightscout took the doc. */
    private suspend fun putSettings(identifier: String, runningConfig: JsonObject): Boolean {
        val client = nsClientV3Plugin().nsAndroidClient ?: run {
            aapsLogger.debug(LTag.NSCLIENT, "updateSettings($identifier) skipped: no Nightscout client yet")
            return false
        }
        val doc = buildJsonObject {
            // NS APIv3 validateCommon requires date / utcOffset / app on UPDATE; all three are
            // immutable after first create, so pick stable constants — the doc represents a
            // configuration snapshot, not an event in time.
            put("date", DOC_DATE)
            put("utcOffset", 0)
            put("app", "AAPS")
            put("schemaVersion", SCHEMA_VERSION)
            put("runningConfig", runningConfig)
        }
        return runCatching { client.updateSettings(identifier, doc) }
            .onSuccess { resp ->
                if (resp.response in 200..299) {
                    val ts = resp.lastModified?.let { dateUtil.dateAndTimeAndSecondsString(it) } ?: "?"
                    nsClientRepository.addLog("► SETTINGS", "$identifier srvModified=$ts")
                } else {
                    aapsLogger.error(LTag.NSCLIENT, "updateSettings($identifier) HTTP ${resp.response}: ${resp.errorResponse}")
                    nsClientRepository.addLog("✕ SETTINGS", "$identifier HTTP ${resp.response} ${resp.errorResponse ?: ""}")
                }
            }
            .onFailure { e ->
                aapsLogger.error(LTag.NSCLIENT, "updateSettings($identifier) failed", e)
                nsClientRepository.addLog("✕ SETTINGS", "$identifier ${e.message}")
            }
            .getOrNull()?.response in 200..299
    }

    companion object {

        private const val SCHEMA_VERSION = 1
        private const val COLD_DEBOUNCE_MS = 5_000L
        private const val HOT_DEBOUNCE_MS = 1_000L
        private const val WS_RECONNECT_DEBOUNCE_MS = 3_000L

        // Waits before a cold doc that was not published is tried again: the first, and the longest one it doubles to.
        // The first is short because a new client waits for the roster in it.
        private const val COLD_RETRY_FIRST_MS = 30_000L
        private const val COLD_RETRY_MAX_MS = 10L * 60L * 1000L

        // 1 ms past NS APIv3 MIN_TIMESTAMP (946684800000 = 2000-01-01 UTC). Required by
        // validateCommon and immutable after create — kept constant so every update matches.
        private const val DOC_DATE = 946684800001L
    }
}
