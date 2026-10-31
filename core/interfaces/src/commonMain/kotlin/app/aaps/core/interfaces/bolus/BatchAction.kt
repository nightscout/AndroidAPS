package app.aaps.core.interfaces.bolus

import app.aaps.core.data.model.HasIDs
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources

/**
 * One action in a multi-action batch ([MasterActionExecutor.prepareBatch]) — the dose/carbs a dialog submits
 * alongside a temp target, applied by the master in one transaction. Domain form; the wire form is
 * `BatchActionDto` in `:core:nssdk`. A batch carries **at most one** [Bolus], **at most one** [TempTarget],
 * and **at most one** [ProfileSwitch].
 */
sealed interface BatchAction {

    /**
     * A FIXED bolus/carbs (capped, never recomputed). [recordOnly] persists it without a pump command (a pen
     * bolus the user gave outside AAPS) — and is **not** constraint-capped (a record of what was given). [iCfg]
     * is the logged insulin's config for the record-only case; null for a delivery (the master uses its active).
     * [quickWizardGuid] tags an INSULIN/CARBS QuickWizard batch with the originating entry so the MASTER marks it
     * used on a successful commit (lastUsed cooldown) — the master is SOT and republishes it; the client never
     * writes the synced QuickWizard pref itself. Null for a dialog/wear batch (those don't carry a QuickWizard).
     * [alarm] asks the master to start the "time to eat" timer for [carbsTimeOffsetMinutes] once the entry is
     * done; it only counts with carbs in the future.
     */
    data class Bolus(
        val insulin: Double,
        val carbs: Int,
        val carbsTimeOffsetMinutes: Int,
        val carbsDurationHours: Int,
        val recordOnly: Boolean,
        val notes: String,
        val timestamp: Long,
        val iCfg: ICfg?,
        val eCarbsGrams: Int = 0,
        val eCarbsDelayMinutes: Int = 0,
        val eCarbsDurationHours: Int = 0,
        val quickWizardGuid: String? = null,
        val alarm: Boolean = false
    ) : BatchAction

    /**
     * A temp target. [startOffsetMinutes] is from now (an eating-soon/activity TT starts now even when the
     * bolus is back-dated). [reason] is the [app.aaps.core.data.model.TT.Reason] text.
     */
    data class TempTarget(
        val reason: String,
        val lowMgdl: Double,
        val highMgdl: Double,
        val durationMinutes: Int,
        val startOffsetMinutes: Int,
        val notes: String? = null
    ) : BatchAction

    /**
     * A profile switch by [percentage] / [timeShiftHours] for [durationMinutes] (0 = until the next switch).
     * [profileName] null → switch the **currently active** profile (wear / CPP); non-null → switch to that
     * **named** profile from the master's profile store. The master resolves the store by name, so a client can
     * relay a named switch the master executes. [notes] is an optional user note.
     *
     * [iCfg] is the insulin to stamp on the resulting switch, supplied by callers that asked the user —
     * the pump fill/prime and activation flows. Leave it null and the master resolves the insulin in
     * force (running profile, else a pending switch); if neither exists the batch is refused at prepare,
     * because the insulin list is a catalogue to choose from, never a source of "the current one".
     *
     * [timestamp] is the time the user picked for the switch; 0 means now (as for [Bolus]). A time in the
     * past back-dates the switch, a time in the future starts it later — the master arms a timer for that
     * start (`ProfileSwitchScheduler`). The master bounds it by
     * [app.aaps.core.data.configuration.Constants.PROFILE_SWITCH_TIME_OFFSET] against its own clock.
     */
    data class ProfileSwitch(
        val percentage: Int,
        val timeShiftHours: Int,
        val durationMinutes: Int,
        val profileName: String? = null,
        val notes: String? = null,
        val iCfg: ICfg? = null,
        val timestamp: Long = 0L
    ) : BatchAction

    /**
     * A loop running-mode change ([RM.Mode]). [durationMinutes] is required (>0) for the temporary modes
     * (SUSPENDED_BY_USER / DISCONNECTED_PUMP) and 0 for the loop modes. The master re-validates the [mode] is a
     * legal transition (`Loop.allowedNextModes`) at prepare time, so a client/watch can relay a mode the master owns.
     */
    data class RunningMode(
        val mode: RM.Mode,
        val durationMinutes: Int = 0
    ) : BatchAction

    /**
     * A manual temp basal. [rate] is a percent (when [isPercent] = true) or an absolute rate in U/h. The dialog
     * produces it in the pump's native style; on a client the VirtualPump mirrors the master's pump (via
     * RunningConfiguration), so the style is already the master's. The master re-validates against its CURRENT pump
     * and rejects on a style mismatch (client config briefly out of sync), then caps + applies it.
     */
    data class TempBasal(
        val rate: Double,
        val isPercent: Boolean,
        val durationMinutes: Int
    ) : BatchAction

    /** A manual extended bolus: [insulin] U over [durationMinutes] (mirrors [Bolus] — the master caps + delivers). */
    data class ExtendedBolus(
        val insulin: Double,
        val durationMinutes: Int
    ) : BatchAction

    /** Stop the running temp basal on the master's pump (the master cancels its CURRENT TBR — no params to relay). */
    data object CancelTempBasal : BatchAction

    /** Stop the running extended bolus on the master's pump (the master cancels its CURRENT extended bolus). */
    data object CancelExtendedBolus : BatchAction

    /**
     * Activate the insulin config [iCfg]: the master re-applies its CURRENT active profile with this insulin
     * (`ProfileFunction.createProfileSwitchWithNewInsulin`). Only the insulin config travels — the master is
     * authoritative for the profile it re-applies. ≤1 per batch. Mirrors [ProfileSwitch] (a config activation).
     */
    data class InsulinActivate(val iCfg: ICfg) : BatchAction

    /**
     * A careportal therapy event (BG check, note, exercise, sensor/site/cartridge change, …): metadata the
     * master persists (it is the SOLE writer) and that syncs back to the client via NS treatments. Carries no
     * dose; applied independently in [MasterActionExecutor.prepareBatch]/confirm. [glucoseMgdl] is canonical
     * mg/dL (the master sanity-clamps it). Unlike the ≤1 action types, the executor handles these as a list
     * (defensive — clients currently send one event per batch).
     */
    data class TherapyEvent(
        val teType: TE.Type,
        val timestamp: Long,
        val glucoseMgdl: Double? = null,
        val glucoseType: TE.MeterType? = null,
        val durationMinutes: Int = 0,
        val note: String? = null,
        val location: TE.Location? = null,
        val arrow: TE.Arrow? = null,
        // Audit source used as the prepare/commit source on the SENDING device. A relayed (client→master) event is
        // logged by the master as Sources.NSClient; the master does not read this off the wire (see applyTherapyEvent).
        val source: Sources
    ) : BatchAction

    /**
     * An EDIT of an existing therapy event's metadata (location / arrow / note) — the management screen's inline edit.
     * Unlike [TherapyEvent] (create, insert-if-new), the master LOCATES its own copy by [timestamp]+[teType] (the
     * cross-device identity for treatments) and UPDATES it; a missing target is rejected (it can't edit a deleted
     * event). [note] is applied verbatim, including null, so clearing a note works. Carries no dose. ≥0, list-handled.
     */
    data class TherapyEventEdit(
        val teType: TE.Type,
        val timestamp: Long,
        val location: TE.Location? = null,
        val arrow: TE.Arrow? = null,
        val note: String? = null,
        // See [TherapyEvent.source]: audit source on the sending device; a relayed edit is logged by the master.
        val source: Sources
    ) : BatchAction

    /**
     * Remove existing records of one [kind]. The master is the only writer: a client never removes a record itself,
     * because the master would keep using it. Only the Nightscout ids go on the wire; they are the ids every instance
     * shares. The master finds its own copies by them, describes them in the confirmation and removes them, and the
     * change comes back to every client through Nightscout. A record the master does not have (for example one
     * entered in Nightscout that the master never took) is not removed, and the confirmation says so.
     *
     * List-handled: one action per kind, any number per batch.
     */
    data class Remove(val kind: Kind, val records: List<Ref>) : BatchAction {

        /** The record types a list screen can remove. */
        enum class Kind {
            BOLUS, CARBS, BOLUS_CALCULATOR_RESULT, TEMPORARY_TARGET, TEMPORARY_BASAL, EXTENDED_BOLUS,
            PROFILE_SWITCH, RUNNING_MODE, THERAPY_EVENT, GLUCOSE_VALUE, CALIBRATION
        }

        /**
         * One record. [nsId] is what the master looks it up by. [localId] is the database id on the device that
         * built the action: the master uses it for its own screens (a master without Nightscout has no Nightscout
         * ids). It never goes on the wire.
         */
        data class Ref(val nsId: String?, val localId: Long? = null)

        companion object {

            /** The removal of [records] of [kind] as stored on this device, or null when there are none. */
            fun of(kind: Kind, records: Collection<HasIDs>): Remove? =
                records.takeIf { it.isNotEmpty() }?.let { list -> Remove(kind, list.map { Ref(nsId = it.ids.nightscoutId, localId = it.id) }) }
        }
    }

    /**
     * Remove every "AndroidAPS started" event. The events carry the text in the language of the device that wrote
     * them, so each device matches its own text instead of a list of records. ≤1 per batch.
     */
    data object RemoveStartEvents : BatchAction

    /**
     * A calibration: the fingerstick [glucoseMgdl] (mg/dL) taken now. The master pairs it with ITS latest sensor
     * reading and checks the conditions (sensor session, warm-up, BG changing too fast) itself, so the loop's BG
     * is calibrated on the device that doses. ≤1 per batch.
     */
    data class Calibration(val glucoseMgdl: Double) : BatchAction
}
