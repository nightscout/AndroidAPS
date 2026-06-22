package app.aaps.core.nssdk.localmodel.configuration

import kotlinx.serialization.Serializable

/**
 * Typed payload of the NS `settings/aaps` doc's `runningConfig` block.
 *
 * Carries the master AAPS install's currently selected plugin set and their owned
 * preference values, applied by clients via [app.aaps.core.nssdk.interfaces.RunningConfiguration].
 *
 * Lives in the settings collection, not in devicestatus — though the field shape
 * was originally introduced for the (now-removed) devicestatus piggyback channel.
 */
@Serializable
data class NSRunningConfiguration(
    val pump: String? = null,
    val version: String? = null,
    // Master-computed flag mirrored READ-ONLY by clients: whether the master's ACTIVE pump fakes temp basals via
    // extended boluses (e.g. Dana "Use Extended"). Cold-doc pump-config state alongside [pump]; the client applies it
    // verbatim to its VirtualPump.fakeDataDetected and must NOT derive it itself. Null = an older master that doesn't
    // publish it → client leaves its own flag untouched.
    val isFakingTempsByExtendedBoluses: Boolean? = null,
    val activeScene: NSActiveScene? = null,
    // Flat block of cold-channel synced preference values (key string → value serialized as string),
    // driven by each key's SyncSpec. Applied with "master wins" on the client; client→master edits
    // travel separately via the signed Client-Control PreferencesUpdate channel.
    val syncedPrefs: Map<String, String>? = null,
    // Computed runtime flag carried in the "hot" doc alongside [activeScene] — whether autosens
    // is actually in use on the master. Top-level so it can ride the small hot doc and refresh on
    // scene lifecycle events.
    val usedAutosensOnMainPhone: Boolean? = null,
    val authorizedClients: NSAuthorizedClients? = null
)

/**
 * Master-published roster of clientIds that are currently authorized to issue commands.
 * Only `Active` entries are exposed — Pending pairings (not yet handshook) are omitted
 * so a client checking membership doesn't get false-positives during the pairing window.
 *
 * Block presence is the backward-compat marker:
 * - **Block absent** (null): older master, no signal. Clients must not infer orphan status.
 * - **Block present with our clientId in [clientIds]**: authorized.
 * - **Block present without our clientId**: orphan (revoked or master-wiped) — surface re-pair UI.
 *
 * Only `clientId` strings are exposed. Names, timestamps, secrets stay master-local.
 */
@Serializable
data class NSAuthorizedClients(
    val clientIds: List<String> = emptyList()
)

/**
 * Wire view of the currently running scene published by the master.
 *
 * Carries the scene definition id, activation timing, lifecycle phase, and the **NS
 * identifiers** of the records the scene created (TempTarget / ProfileSwitch /
 * RunningMode / TherapyEvent). Local Room IDs and the master-only `priorSmb` flag are
 * deliberately not shipped.
 *
 * [lifecycle] is master-owned: "ACTIVE" while the scene is running, "EXPIRED" once
 * master has run its expiry side-effects (revert SMB, chain scheduling). Encoded as
 * String so this DTO stays free of any core/data dependency; consumers map back to the
 * [app.aaps.core.data.model.SceneLifecycle] enum. Null is treated as ACTIVE for
 * backward compatibility with masters that pre-date this field.
 *
 * NS IDs may be null transiently while the corresponding record is still uploading on the
 * master; they fill in on subsequent publishes. Master clears the entire `activeScene`
 * block when the scene is dismissed.
 */
@Serializable
data class NSActiveScene(
    val sceneId: String,
    val activatedAt: Long,
    val durationMs: Long,
    val lifecycle: String? = null,
    val ttNsId: String? = null,
    val psNsId: String? = null,
    val rmNsId: String? = null,
    val teNsId: String? = null
)
