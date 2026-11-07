package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The closed family of payloads a paired client can send to a master inside a
 * [SignedEnvelope]. New message types must extend this sealed hierarchy — that
 * gives the receiver compile-time exhaustiveness in `when` dispatch and avoids
 * the silent-typo failure mode of string-keyed message types.
 *
 * Wire format: each variant is serialized polymorphically via kotlinx
 * serialization. The discriminator field on the wire is named `type` (kotlinx's
 * default). The signed bytes are the JSON of the variant including the
 * discriminator — see [SignedEnvelope.payload].
 *
 * **The `@SerialName` strings are the wire contract — once shipped they cannot
 * be renamed without a flag day.** The Kotlin class names can be refactored
 * freely; the SerialNames cannot. Add new variants, do not rename existing.
 */
@Serializable
sealed class ClientControlMessage {

    /**
     * First signed message a freshly paired client sends to the master.
     * Promotes the master's pairing entry from Pending → Active. Sent with
     * counter = 1 (the first envelope after pairing).
     *
     * `protocolVersion` lets either side reject a peer it cannot speak to.
     */
    @Serializable
    @SerialName("hello")
    data class Hello(
        val protocolVersion: Int = 1
    ) : ClientControlMessage()

    /**
     * Liveness probe. The master does nothing but acknowledge it — the signed ACK ("pong") is an
     * authenticated, real-time "master alive now" signal the client folds into `masterReachable`,
     * clearing the offline banner without waiting for the next devicestatus heartbeat. Sent with
     * `wantsAck = true`.
     */
    @Serializable
    @SerialName("ping")
    data object Ping : ClientControlMessage()

    /**
     * Client asks the master to PREPARE activating the named scene: the master resolves the scene against ITS OWN
     * state, gates it (validateActivation), authors the confirmation lines from the scene's actions, parks it, and
     * returns the preview in the signed ack. TWO-STEP — a [SceneCommit] follows and actually activates it. Nothing
     * runs until commit. `durationMinutes = null` → the scene's stored default; an explicit value overrides.
     */
    @Serializable
    @SerialName("scene_prepare")
    data class ScenePrepare(
        val sceneId: String,
        val durationMinutes: Int? = null
    ) : ClientControlMessage()

    /**
     * Client confirms a prepared scene: the master activates the parked scene matching [bolusId] EXACTLY once
     * (a re-sent commit finds the slot drained → no double-activate). Separate from [BolusCommit] so the scene and
     * bolus consume-once id-spaces never collide.
     */
    @Serializable
    @SerialName("scene_commit")
    data class SceneCommit(
        val bolusId: Long
    ) : ClientControlMessage()

    /**
     * Tells the master to deactivate whatever scene is currently active.
     *
     * `triggerChain = true` mirrors master's "Skip to <ChainTarget>" choice — after deactivating,
     * the master re-checks canChain (target enabled, loop running, pump initialized, profile set)
     * and activates the active scene's configured chain target if all are met. `false` is a plain
     * stop (chain dies). Wire intentionally carries no target id: the master uses its own current
     * chain config so client-side staleness can't trigger an unintended scene.
     */
    @Serializable
    @SerialName("scene_stop")
    data class SceneStop(
        val triggerChain: Boolean = false
    ) : ClientControlMessage()

    /**
     * Generic client→master push of plain, bidirectionally-synced preference values (keys carrying a
     * `SyncSpec(direction = Bidirectional)`). One message batches every locally-changed synced key, so
     * new synced settings need no new wire type — they just appear as another entry in [prefs].
     *
     * Each entry's value is the preference serialized as a string (the master parses it back using the
     * key's declared type), plus a per-key `lastModified` the master applies last-writer-wins by.
     * Master adopts strictly-newer values and republishes via the running-config doc.
     */
    @Serializable
    @SerialName("preferences_update")
    data class PreferencesUpdate(
        val prefs: Map<String, PrefEntry>
    ) : ClientControlMessage()

    /**
     * Client asks the master to PREPARE a QuickWizard (WIZARD-mode) bolus: the master computes + constraint-caps
     * the dose on its own live state and returns the preview in the signed ack. Nothing is delivered yet. [guid]
     * identifies the QuickWizard entry (defs are synced to the client).
     */
    @Serializable
    @SerialName("bolus_prepare")
    data class BolusPrepare(
        val guid: String
    ) : ClientControlMessage()

    /**
     * Client confirms a prepared bolus: the master delivers the parked dose matching [bolusId] exactly once
     * (a re-sent commit finds the slot drained → no double-dose). [asAdvisor] = the user took the high-BG
     * "correct now, eat later" branch → deliver the correction-only advisor bolus instead of the carb wizard bolus.
     */
    @Serializable
    @SerialName("bolus_commit")
    data class BolusCommit(
        val bolusId: Long,
        val asAdvisor: Boolean = false,
        val correctionU: Double = 0.0,
    ) : ClientControlMessage()

    /**
     * Client asks the master to PREPARE a MANUAL bolus-wizard bolus from these raw inputs: the master recomputes
     * the dose on its OWN profile/COB/IOB, constraint-caps it, and returns the preview in the signed ack. Nothing
     * is delivered yet (a `BolusCommit` follows). Mirrors `BolusPrepare` (QuickWizard) but carries the full wizard
     * inputs instead of a synced guid.
     */
    @Serializable
    @SerialName("wizard_prepare")
    data class WizardPrepare(
        val bg: Double,
        val carbs: Int,
        val percentage: Int,
        val directCorrection: Double,
        val carbTime: Int,
        val useBg: Boolean,
        val useCob: Boolean,
        val useIob: Boolean,
        val useTt: Boolean,
        val useTrend: Boolean,
        val alarm: Boolean,
        val notes: String,
        val eCarbsGrams: Int = 0,
        val eCarbsDelayMinutes: Int = 0,
        val eCarbsDurationHours: Int = 0,
        // null → the master's active profile; a name → that stored profile (additive, default keeps old clients wire-compatible).
        val profileName: String? = null
    ) : ClientControlMessage()

    /**
     * Client asks the master to PREPARE a multi-action batch (≤1 FIXED bolus/carbs + ≤1 temp target): the master caps
     * the delivery (a record-only is kept as given), builds the MERGED confirmation lines for the whole batch, parks,
     * and returns the preview in the signed ack. TWO-STEP — a [BolusCommit] follows and applies the bundle (the TT per
     * the ordering rule). The replacement for the one-step [BatchApply].
     */
    @Serializable
    @SerialName("batch_prepare")
    data class BatchPrepare(
        val actions: List<BatchActionDto>
    ) : ClientControlMessage()

    /**
     * Client acknowledged (dismissed/muted) the relayed bolus-delivery-failure alarm on its side → ask the master to
     * clear ITS copy too. One-directional by design: the master silencing its own alarm must NOT clear the client's
     * (the remote initiator has to see its failed bolus). Fire-and-forget (no ack) — see ClientControlReceiver.
     */
    @Serializable
    @SerialName("dismiss_alarm")
    data object DismissAlarm : ClientControlMessage()

    /**
     * Client asks the master to abort the in-progress bolus it initiated (the one being mirrored to this client).
     * Fire-and-forget: the master calls commandQueue.cancelAllBoluses() and the resulting progress terminal mirrors
     * back. Safe (only ever delivers less) and idempotent.
     */
    @Serializable
    @SerialName("stop_bolus")
    data object StopBolus : ClientControlMessage()
}

/** One synced preference on the wire: its value (serialized as a string) and edit timestamp. */
@Serializable
data class PrefEntry(
    val value: String,
    val lastModified: Long
)

/**
 * One action on the wire for [ClientControlMessage.BatchPrepare] — a flat, type-tagged union (avoids polymorphic
 * serializer setup). [type] selects which fields are meaningful (`bolus` vs `temp_target` vs `profile_switch` vs
 * `running_mode`). [durationMinutes] is shared by `temp_target`, `profile_switch`, and `running_mode`.
 *
 * Because every field carries a default, ANY field combination deserializes; this DTO does NOT self-validate. The
 * MASTER is the single authority: per [type] it validates that the required fields for that action are present/sane
 * and REJECTS an unknown [type] value (the `TYPE_*` constants below are the closed set). A client cannot smuggle an
 * action by setting the wrong fields — the master only reads the fields its `when(type)` branch consumes.
 */
@Serializable
data class BatchActionDto(
    val type: String,
    // bolus
    val insulin: Double = 0.0,
    val carbs: Int = 0,
    val carbsTimeOffsetMinutes: Int = 0,
    val carbsDurationHours: Int = 0,
    val recordOnly: Boolean = false,
    val notes: String = "",
    val timestamp: Long = 0L,
    val iCfgJson: String? = null,
    // temp_target
    val reason: String? = null,
    val lowMgdl: Double = 0.0,
    val highMgdl: Double = 0.0,
    val durationMinutes: Int = 0,
    val startOffsetMinutes: Int = 0,
    // profile_switch (also reuses [notes]; [profileName] null → active-profile switch)
    val percentage: Int = 0,
    val timeShiftHours: Int = 0,
    val profileName: String? = null,
    // running_mode ([RM.Mode] name; reuses [durationMinutes] for the temporary modes)
    val runningMode: String? = null,
    // temp_basal ([rate] = percent or absolute U/h per [isPercent]; reuses [durationMinutes]).
    // extended_bolus reuses [insulin] + [durationMinutes].
    val rate: Double = 0.0,
    val isPercent: Boolean = false,
    // therapy_event (careportal): reuses [timestamp], [notes], [durationMinutes]; carries its own glucose/type/site
    val teType: String? = null,
    val glucoseMgdl: Double? = null,
    val meterType: String? = null,
    val location: String? = null,
    val arrow: String? = null,
    val source: String? = null, // round-trips for symmetry; the master ignores it (relayed events are logged as Sources.NSClient)
    // bolus eCarbs split: extended carbs amount, delay, and duration (0 = no eCarbs)
    val eCarbsGrams: Int = 0,
    val eCarbsDelayMinutes: Int = 0,
    val eCarbsDurationHours: Int = 0,
    // bolus: originating QuickWizard guid (INSULIN/CARBS mode) so the master marks the entry used on commit (lastUsed)
    val quickWizardGuid: String? = null
) {

    companion object {

        const val TYPE_BOLUS = "bolus"
        const val TYPE_TEMP_TARGET = "temp_target"
        const val TYPE_PROFILE_SWITCH = "profile_switch"
        const val TYPE_RUNNING_MODE = "running_mode"
        const val TYPE_TEMP_BASAL = "temp_basal"
        const val TYPE_EXTENDED_BOLUS = "extended_bolus"

        // cancel_temp_basal / cancel_extended_bolus carry no fields — the master cancels its CURRENT TBR / extended bolus.
        const val TYPE_CANCEL_TEMP_BASAL = "cancel_temp_basal"
        const val TYPE_CANCEL_EXTENDED_BOLUS = "cancel_extended_bolus"

        // insulin_activate reuses [iCfgJson] — the master re-applies its active profile with this insulin config.
        const val TYPE_INSULIN_ACTIVATE = "insulin_activate"

        // therapy_event (careportal): the master persists a TherapyEvent (it is the sole writer; syncs back via NS).
        const val TYPE_THERAPY_EVENT = "therapy_event"

        // therapy_event_edit: the master UPDATES an existing TherapyEvent it locates by teType+timestamp (reuses the
        // teType/timestamp/location/arrow/notes/source fields). Distinct from create so it never insert-if-news.
        const val TYPE_THERAPY_EVENT_EDIT = "therapy_event_edit"
    }
}
