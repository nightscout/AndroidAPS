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
import app.aaps.plugins.sync.nsclientV3.services.RunningConfigurationPublisher.Companion.COLD_DEBOUNCE_MS
import app.aaps.plugins.sync.nsclientV3.services.RunningConfigurationPublisher.Companion.HOT_DEBOUNCE_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Master-side publisher for the NS running-config `settings` documents.
 *
 * Splits the config across two docs by write-frequency (see [SettingsIdentifiers]):
 *  - Cold ([SettingsIdentifiers.COLD]): plugin config + overview + definitions + authorized
 *    clients. Triggered by [RunningConfigurationKeys.observableKeys] changes, plugin switches
 *    ([EventConfigBuilderChange]) and authorized-client changes; debounced [COLD_DEBOUNCE_MS].
 *  - Hot ([SettingsIdentifiers.STATE]): active scene + computed runtime flags. Triggered by
 *    [RunningConfigurationKeys.hotKeys] (scene lifecycle); debounced [HOT_DEBOUNCE_MS].
 *
 * Document shape (both): `{ schemaVersion: 1, runningConfig: <…> }`, upserted via `updateSettings`.
 *
 * Started by [NSClientV3Plugin.onStart] and stopped by `onStop`.
 * Only active when `config.APS` is true (master role); no-op on aapsclient.
 */
@OptIn(FlowPreview::class)
@Singleton
class RunningConfigurationPublisher @Inject constructor(
    private val runningConfiguration: RunningConfiguration,
    private val runningConfigurationKeys: RunningConfigurationKeys,
    private val nsClientV3Plugin: Provider<NSClientV3Plugin>,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val nsClientRepository: NSClientRepository,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val authorizedRepository: AuthorizedClientsRepository,
) {

    private var job: Job? = null

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
                val coldPublished = publishCold()
                publishHot()
                // Poll-mode race: the pump may not be initialized yet at start, so the initial cold publish
                // emits an empty payload and is skipped — leaving a poll-mode client config-less (no WS-connect
                // event to retry on). Schedule one delayed retry via the existing cold collector so it picks up
                // once the pump is ready.
                if (!coldPublished) launch {
                    delay(POLL_INITIAL_RETRY_MS)
                    requestColdRepublish()
                }
            }

            val switchTrigger: Flow<Unit> = rxBus.toFlow(EventConfigBuilderChange::class.java).map { }
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
                    .collect { publishCold() }
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
                nsClientV3Plugin.get().wsConnectedFlow
                    .filter { it }
                    .debounce(WS_RECONNECT_DEBOUNCE_MS)
                    .collect {
                        publishCold()
                        publishHot()
                    }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // Cold doc: full plugin/overview/definition config + the authorized-clients roster.
    // Returns true if a doc was published, false if skipped because the pump is not initialized yet.
    private suspend fun publishCold(): Boolean {
        val payload = runningConfiguration.configuration()
        if (payload.length() == 0) return false // pump not initialized yet
        // Append the authorized-clients roster directly on the runningConfig JSONObject — the
        // canonical RunningConfiguration plugin (in :plugins:configuration) cannot reach
        // AuthorizedClientsRepository without a new inter-module dependency, so we attach
        // this master-local block here instead. Only Active entries are exposed.
        val activeClientIds = authorizedRepository.current(dateUtil.now())
            .filter { it.state == ClientState.Active }
            .map { it.clientId }
        payload.put("authorizedClients", JSONObject().put("clientIds", JSONArray(activeClientIds)))
        putSettings(SettingsIdentifiers.COLD, payload)
        return true
    }

    // Hot doc: active scene + computed runtime flags. Small and frequently republished.
    private suspend fun publishHot() {
        putSettings(SettingsIdentifiers.STATE, runningConfiguration.activeSceneConfiguration())
    }

    private suspend fun putSettings(identifier: String, runningConfig: JSONObject) {
        val client = nsClientV3Plugin.get().nsAndroidClient ?: return
        val doc = JSONObject().apply {
            // NS APIv3 validateCommon requires date / utcOffset / app on UPDATE; all three are
            // immutable after first create, so pick stable constants — the doc represents a
            // configuration snapshot, not an event in time.
            put("date", DOC_DATE)
            put("utcOffset", 0)
            put("app", "AAPS")
            put("schemaVersion", SCHEMA_VERSION)
            put("runningConfig", runningConfig)
        }
        runCatching { client.updateSettings(identifier, doc) }
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
    }

    companion object {

        private const val SCHEMA_VERSION = 1
        private const val COLD_DEBOUNCE_MS = 5_000L
        private const val HOT_DEBOUNCE_MS = 1_000L
        private const val WS_RECONNECT_DEBOUNCE_MS = 3_000L

        // Poll-mode only: delay before retrying the initial cold publish when the pump was not yet
        // initialized at start. One shot, fed through the debounced cold collector via requestColdRepublish().
        private const val POLL_INITIAL_RETRY_MS = 10_000L

        // 1 ms past NS APIv3 MIN_TIMESTAMP (946684800000 = 2000-01-01 UTC). Required by
        // validateCommon and immutable after create — kept constant so every update matches.
        private const val DOC_DATE = 946684800001L
    }
}
