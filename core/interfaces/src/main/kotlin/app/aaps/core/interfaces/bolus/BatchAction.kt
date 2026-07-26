package app.aaps.core.interfaces.bolus

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources

/**
 * One action in a multi-action batch ([WizardBolusExecutor.prepareBatch]) — the dose/carbs a dialog submits
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
        val quickWizardGuid: String? = null
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
     */
    data class ProfileSwitch(
        val percentage: Int,
        val timeShiftHours: Int,
        val durationMinutes: Int,
        val profileName: String? = null,
        val notes: String? = null
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
     * dose; applied independently in [WizardBolusExecutor.prepareBatch]/confirm. [glucoseMgdl] is canonical
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
}
