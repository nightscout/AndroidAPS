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
     * Check if an iCfg already exists in the list.
     * @param iCfg
     * @param excludeIndex index to exclude for duplicated name identification (-1 for none)
     */
    fun insulinAlreadyExists(iCfg: ICfg, excludeIndex: Int = -1): Boolean

    /**
     * Return index of the insulin within InsulinManager. -1 if iCFg is not found
     * @param iCfg
     */
    fun insulinIndex(iCfg: ICfg?): Int

    /**
     * Calculate the suffix to be shown in UI (include potential index to prevent duplication names).
     */
    fun buildDisplaySuffix(nickname: String, peak: Int, dia: Double, concentration: Double, excludeIndex: Int = -1): String
}
