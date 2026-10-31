package app.aaps.core.interfaces.insulin

import app.aaps.core.data.model.ICfg

/**
 * Management interface for insulin CRUD operations.
 * Separated from [Insulin] (read-only consumption) so that APS, pumps, and profiles
 * don't see mutation methods.
 */
interface InsulinManager {

    /**
     * All configured insulins. Read it, do not change it: a sync can replace the whole list at any
     * moment. Change the catalogue only through [addNewInsulin], [updateInsulin] and [removeInsulin],
     * which find their target and store the result in one step. For the same reason, do not keep an
     * index into this list across calls.
     */
    val insulins: ArrayList<ICfg>

    /** Reload insulin list from persisted settings */
    fun loadSettings()

    /**
     * The exact serialized configuration string of the most recent LOCAL edit ([addNewInsulin],
     * [updateInsulin], [removeInsulin]). Lets a UI observing `InsulinConfiguration` recognize its own
     * echoes (which equal this) and tell them apart from a genuine external (client→master) push —
     * which arrives via putRemote and never updates this — independent of write-ordering /
     * coroutine-dispatch timing.
     */
    val lastStoredConfiguration: String

    /** Add a new insulin to the end of the list. Returns the stored copy. */
    fun addNewInsulin(newICfg: ICfg, ue: Boolean = true, keepName: Boolean = false): ICfg

    /**
     * Replace the insulin labelled [originalLabel] with the curve, concentration and nickname of
     * [edited], under a new label built from them, and store the list. The label of [edited] is ignored.
     */
    fun updateInsulin(originalLabel: String, edited: ICfg): UpdateResult

    sealed interface UpdateResult {

        /** Stored under [label]. */
        data class Updated(val label: String) : UpdateResult

        /** No insulin has the original label any more: a sync removed or renamed it. Nothing was stored. */
        data object NotFound : UpdateResult

        /** Another insulin already has [label]. Nothing was stored. */
        data class LabelTaken(val label: String) : UpdateResult
    }

    /**
     * Remove the insulin labelled [label]. No-op when no insulin has that label, and on a
     * single-element list — the list is never emptied.
     *
     * Which insulin is "selected" is the caller's business: this is a catalogue, and the insulin actually
     * in use is derived from the running profile, never from a position in this list.
     */
    fun removeInsulin(label: String)

    /** Available insulin type presets (templates) */
    fun insulinTemplateList(): List<InsulinType>

    /** Available concentration types */
    fun concentrationList(): List<ConcentrationType>

    /**
     * Build insulin Label (Nickname and suffix calculated with Peak, DIA, Concentration) and a potential index to prevent duplication names.
     * @param excludeIndex index to exclude for duplicated name identification (-1 for none)
     */
    fun buildFullName(nickname: String, peak: Int, dia: Double, concentration: Double, excludeIndex: Int = -1): String

    /**
     * Calculate the suffix for insulin migration.
     */
    fun buildSuffix(peak: Int, dia: Double, concentration: Double): String

    /**
     * True when the list has an insulin with the same DIA, peak and concentration as [iCfg]. The label is
     * ignored: the list rebuilds every label at start, so a label alone can never prove that an insulin
     * is missing.
     */
    fun insulinWithSameCurveExists(iCfg: ICfg): Boolean

    /**
     * Puts the insulin the v3 to v4 migration worked out into the catalogue, and returns the stored copy
     * (with its label, which the caller stamps onto the historical records).
     *
     * This exists because of the order the two run in. A v3 install has no stored catalogue, so the first
     * load has nothing to show the insulin pickers and invents a default - and the migration, which knows
     * the real insulin, then added it BESIDE that default. The user came out of the upgrade with an insulin
     * they never had, sitting at `insulins[0]`, which is what an activation wizard offers first.
     *
     * So an invented default is REPLACED here rather than kept. A catalogue that was really stored is left
     * alone and [iCfg] is only added to it when its curve is missing, which is what makes this safe to call
     * at every start: the migration runs again on every launch for as long as any record still carries the
     * v33 sentinel.
     *
     * A blank label is built from the nickname and the curve, like any other entry; a label that is already
     * set is kept, because a record migrated by `migration32to33` carries the name v3 gave it.
     */
    fun seedFromMigration(iCfg: ICfg): ICfg

    /**
     * Index of the list entry for [iCfg], or -1. Same label and curve first, then the first entry with
     * the same curve. See [app.aaps.core.data.model.indexOfInsulin].
     */
    fun insulinIndex(iCfg: ICfg?): Int

    /**
     * Calculate the suffix to be shown in UI (include potential index to prevent duplication names).
     */
    fun buildDisplaySuffix(nickname: String, peak: Int, dia: Double, concentration: Double, excludeIndex: Int = -1): String
}
