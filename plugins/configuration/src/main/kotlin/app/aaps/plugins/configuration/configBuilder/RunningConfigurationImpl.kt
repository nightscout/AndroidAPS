package app.aaps.plugins.configuration.configBuilder

import app.aaps.core.data.model.SceneLifecycle
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.RunningConfigurationKeys
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.scenes.ActiveSceneSnapshot
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.localmodel.configuration.NSActiveScene
import app.aaps.core.nssdk.localmodel.configuration.NSRunningConfiguration
import app.aaps.plugins.configuration.R
import dagger.Reusable
import kotlinx.serialization.json.Json
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

@Reusable
class RunningConfigurationImpl @Inject constructor(
    private val activePlugin: ActivePlugin,
    private val activeSceneSync: ActiveSceneSync,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val pumpSync: PumpSync,
    private val notificationManager: NotificationManager,
    private val nsClientRepository: NSClientRepository,
    private val constraintsChecker: ConstraintsChecker
) : RunningConfiguration, RunningConfigurationKeys {

    // Omit null fields so the frequently-published hot doc stays small.
    private val wireJson = Json { explicitNulls = false }

    // called in AAPS mode only
    override fun configuration(): JSONObject {
        val json = JSONObject()
        // Before init completes no pump is selected yet, and activePump.isInitialized() would throw
        // "No pump selected" (PluginStore.activePumpInternal) instead of returning false. Treat
        // "app not initialized" as an earlier "pump not ready" state and return the empty doc —
        // RunningConfigurationPublisher skips an empty payload and retries once init completes.
        if (!config.appInitialized) return json
        val pumpInterface = activePlugin.activePump

        if (!pumpInterface.isInitialized()) return json
        try {
            // Plugin selection + settings + scene definitions all ride the generic key-sync path now
            // (ActivePlugin* keys + the flat syncedPrefs cold block).
            json.put("syncedPrefs", buildSyncedPrefs())
            json.put("pump", pumpInterface.model().description)
            // Mirror the master's active-pump faking flag READ-ONLY to clients (so a follower's VirtualPump shows the
            // right EB capability + interprets emulated-temp EBs correctly) — computed here on the master, never on the client.
            // Reads activePump (the wrapper, which delegates to the real pump on the master); applyCold instead casts
            // activePumpInternal because a client only has VirtualPump (not the PumpWithConcentration wrapper).
            json.put("isFakingTempsByExtendedBoluses", pumpInterface.isFakingTempsByExtendedBoluses)
            json.put("version", config.VERSION_NAME)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return json
    }

    // called in AAPS mode only — the small "hot" doc: active scene (if any) + computed runtime flags.
    override fun activeSceneConfiguration(): JSONObject {
        val json = JSONObject()
        try {
            activeSceneSync.activeSceneSnapshot()?.let { snapshot ->
                // Serialize from the wire DTO so the field set stays in lockstep with
                // [NSActiveScene] (no hand-maintained put() list to drift). explicitNulls=false
                // keeps the doc small by omitting not-yet-resolved NS ids.
                val wire = NSActiveScene(
                    sceneId = snapshot.sceneId,
                    activatedAt = snapshot.activatedAt,
                    durationMs = snapshot.durationMs,
                    lifecycle = snapshot.lifecycle.name,
                    ttNsId = snapshot.ttNsId,
                    psNsId = snapshot.psNsId,
                    rmNsId = snapshot.rmNsId,
                    teNsId = snapshot.teNsId
                )
                json.put("activeScene", JSONObject(wireJson.encodeToString(NSActiveScene.serializer(), wire)))
            }
            json.put("usedAutosensOnMainPhone", constraintsChecker.isAutosensModeEnabled().value())
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return json
    }

    // All cold-synced values (plugin selection, plugin settings, scene/quick-wizard/automation/insulin
    // definitions) are declared via SyncSpec — a change to any republishes the cold doc. Their domain
    // objects self-reload from the key, so there's no reloadInternalState hook.
    override fun observableKeys(): List<NonPreferenceKey> = coldSyncKeys()

    /** Plain preference keys declared to ride the cold running-config doc via [SyncChannel.Cold]. */
    private fun coldSyncKeys(): List<NonPreferenceKey> =
        preferences.getSyncKeys().filter { it.sync?.channel == SyncChannel.Cold }

    // Flat {keyString: valueAsString} block of cold synced prefs. Values serialized as strings so the
    // wire stays a Map<String, String> regardless of the key's underlying type. Boolean/String/Int/
    // UnitDouble are supported (see the `when` below); other types are skipped with a warning.
    //
    // Sync the persisted value via get(..., forSync = true): the user's stored setting, falling back to the
    // COMPUTED default (calculatedDefaultValue) when unset, but WITHOUT simple-mode presentation forcing.
    // Rationale: simple-mode / defaultedBySM is a per-device presentation choice, so the underlying user setting
    // must travel and each device re-applies its own mode logic on read — hence NOT the full effective getter.
    // But a plain raw read would publish the literal default for computed-default keys (NsClientAllowClientControl,
    // AutosensPeriod, …), mis-telling clients the master's operative value; forSync uses the computed default instead.
    private fun buildSyncedPrefs(): JSONObject {
        val out = JSONObject()
        coldSyncKeys().forEach { key ->
            when (key) {
                is BooleanNonPreferenceKey -> out.put(key.key, preferences.get(key, forSync = true).toString())
                is StringNonPreferenceKey  -> out.put(key.key, preferences.get(key))
                is IntNonPreferenceKey     -> out.put(key.key, preferences.get(key, forSync = true).toString())
                is DoubleNonPreferenceKey  -> out.put(key.key, preferences.get(key).toString())   // no computed default → raw
                is UnitDoublePreferenceKey -> out.put(key.key, preferences.getRaw(key).toString())   // raw mg/dl, 1:1
                else                       -> aapsLogger.warn(LTag.CORE, "syncedPrefs: unsupported key type for ${key.key}")
            }
        }
        return out
    }

    // Apply master-published synced prefs on the client. "Master wins": adopt verbatim via putRemote
    // (which suppresses the client→master echo and floors the modified stamp; the wire carries no
    // per-key lastModified yet). A later client edit out-stamps it via max(stored+1, now()).
    /**
     * `putRemote(key, value, 0L)`: the trailing `0L` is the lastModified stamp. Passing `0L` floors
     * the stamp to the minimum so any later user edit (stamped with `max(stored+1, now())`) always
     * out-wins this remotely-adopted value under last-write-wins.
     */
    private fun applySyncedPrefs(prefs: Map<String, String>) {
        prefs.forEach { (keyString, valueString) ->
            val key = preferences.get(keyString) ?: return@forEach
            when (key) {
                is BooleanNonPreferenceKey -> valueString.toBooleanStrictOrNull()?.let { preferences.putRemote(key, it, 0L) }
                is StringNonPreferenceKey  -> preferences.putRemote(key, valueString, 0L)
                is IntNonPreferenceKey     -> valueString.toIntOrNull()?.let { preferences.putRemote(key, it, 0L) }
                is DoubleNonPreferenceKey  -> valueString.toDoubleOrNull()?.let { preferences.putRemote(key, it, 0L) }
                is UnitDoublePreferenceKey -> valueString.toDoubleOrNull()?.let { preferences.putRemote(key, it, 0L) }   // raw mg/dl
                else                       -> aapsLogger.warn(LTag.CORE, "syncedPrefs: unsupported key type for $keyString")
            }
        }
    }

    // Runtime state — published to the separate "hot" doc, not the cold config doc.
    override fun hotKeys(): List<NonPreferenceKey> = listOf(StringNonKey.ActiveScene)

    // called in NSClient mode only — apply the cold config doc (everything except the active scene).
    override fun applyCold(configuration: NSRunningConfiguration) {
        if (!config.AAPSCLIENT) {
            aapsLogger.error(LTag.CORE, "applyCold called on non-client build — ignored")
            return
        }

        configuration.version?.let {
            nsClientRepository.addLog("◄ VERSION", "Received AAPS version $it")
            if (config.VERSION_NAME.startsWith(it).not())
                notificationManager.post(NotificationId.NSCLIENT_VERSION_DOES_NOT_MATCH, R.string.nsclient_version_does_not_match)
        }
        // APS/Sensitivity/Smoothing/Calibration selection is adopted via the synced ActivePlugin* keys
        // (applied below in syncedPrefs → ConfigBuilder's key observer performs the switch).

        configuration.pump?.let {
            if (preferences.get(StringKey.VirtualPumpType) != it) {
                preferences.put(StringKey.VirtualPumpType, it)
                activePlugin.activePump.pumpDescription.fillFor(PumpType.getByDescription(it))
                pumpSync.connectNewPump(endRunning = false) // do not end running TBRs, we call this only to accept data properly
                aapsLogger.debug(LTag.CORE, "Changing pump type to $it")
            }
        }

        configuration.syncedPrefs?.let { applySyncedPrefs(it) }
        // Read-only mirror of the master's active-pump faking flag onto this client's VirtualPump. Applied verbatim
        // (incl. false, so turning it off on the master propagates); null = older master → leave the client flag as-is.
        // activePumpInternal (the real plugin) — NOT activePump, which is a PumpWithConcentration wrapper that isn't a VirtualPump.
        configuration.isFakingTempsByExtendedBoluses?.let { (activePlugin.activePumpInternal as? VirtualPump)?.fakeDataDetected = it }
    }

    // called in NSClient mode only — apply the hot doc (active scene + computed runtime flags).
    // Kept separate from [applyCold] so a cold-doc apply (which carries no activeScene) never
    // clears a running scene.
    override fun applyHot(configuration: NSRunningConfiguration) {
        if (!config.AAPSCLIENT) {
            aapsLogger.error(LTag.CORE, "applyHot called on non-client build — ignored")
            return
        }
        // activeScene: null on the wire means "no scene active" — clear locally.
        // Always pass through (even null) so master-side dismissal propagates.
        activeSceneSync.applyActiveScene(configuration.activeScene?.toSnapshot())
        configuration.usedAutosensOnMainPhone?.let { preferences.put(BooleanNonKey.AutosensUsedOnMainPhone, it) }
    }

    private fun NSActiveScene.toSnapshot() =
        ActiveSceneSnapshot(
            sceneId = sceneId,
            activatedAt = activatedAt,
            durationMs = durationMs,
            // null on the wire = pre-lifecycle master, treat as ACTIVE.
            // Forward-compat: an unknown lifecycle string (newer master adds a value this build doesn't
            // know) → valueOf returns null via runCatching → falls through to ACTIVE as well.
            lifecycle = lifecycle?.let { runCatching { SceneLifecycle.valueOf(it) }.getOrNull() }
                ?: SceneLifecycle.ACTIVE,
            ttNsId = ttNsId,
            psNsId = psNsId,
            rmNsId = rmNsId,
            teNsId = teNsId
        )

}
