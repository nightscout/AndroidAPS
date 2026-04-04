package app.aaps.core.interfaces.insulin

import app.aaps.core.data.model.ICfg

/**
 * Management interface for insulin CRUD operations.
 * Separated from [Insulin] (read-only consumption) so that APS, pumps, and profiles
 * don't see mutation methods.
 */
interface InsulinManager {

    /**
     * Provide default Insulin
     */
    val iCfg: ICfg

    /** All configured insulins */
    val insulins: ArrayList<ICfg>

    /** Index of the insulin currently selected in the management UI */
    var currentInsulinIndex: Int

    /** Reload insulin list from persisted settings */
    fun loadSettings()

    /** Persist current insulin list */
    fun storeSettings()

    /** Add a new insulin to the list. Returns the stored copy. */
    fun addNewInsulin(newICfg: ICfg, ue: Boolean = true, keepName: Boolean = false): ICfg

    /** Remove the insulin at [currentInsulinIndex] */
    fun removeCurrentInsulin()

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
     * Calculate the suffix to be shown in UI (include potential index to prevent duplication names).
     */
    fun buildDisplaySuffix(nickname: String, peak: Int, dia: Double, concentration: Double, excludeIndex: Int = -1): String
}
