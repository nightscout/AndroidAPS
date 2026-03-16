package app.aaps.core.interfaces.insulin

import app.aaps.core.data.model.ICfg

/**
 * Management interface for insulin CRUD operations.
 * Separated from [Insulin] (read-only consumption) so that APS, pumps, and profiles
 * don't see mutation methods.
 */
interface InsulinManager {

    /** All configured insulins */
    val insulins: ArrayList<ICfg>

    /** Index of the insulin currently selected in the management UI */
    var currentInsulinIndex: Int

    /** Reload insulin list from persisted settings */
    fun loadSettings()

    /** Persist current insulin list */
    fun storeSettings()

    /** Add a new insulin to the list. Returns the stored copy. */
    fun addNewInsulin(newICfg: ICfg, ue: Boolean = true): ICfg

    /** Remove the insulin at [currentInsulinIndex] */
    fun removeCurrentInsulin()

    /** Available insulin type presets (templates) */
    fun insulinTemplateList(): List<InsulinType>

    /** Available concentration types */
    fun concentrationList(): List<ConcentrationType>
}
