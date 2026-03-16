package app.aaps.core.interfaces.insulin

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.configuration.ConfigExportImport

interface Insulin : ConfigExportImport {

    val id: InsulinType
    val friendlyName: String

    /**
     * Provide list of available Insulin
     * with no parameter (concentration == null) or without enable_insulin_concentration, only Insulin using the current running concentration will be provided (by default 1.0)
     * if concentration is provided with 0.0, then the overall list of available insulin is provided (external boluses provided by pens)
     * if a specific concentration is provided, then only the list of insulin consistent with selected value will be provided (to manage changing concentration value during Insulin_Change events)
     *
     * @param concentration only provide the list of Insulin using a specific concentration value
     * @return Basal insulin recalculated to U100 units used inside core of AAPS
     */
    fun insulinList(concentration: Double? = null): List<CharSequence> = emptyList()

    /**
     * provide insulin configuration with insulinLabel,
     * if insulinLabel is not found within insulin, then current running iCfg is returned
     *
     * @param insulinLabel insulinLabel of insulin
     * @return ICfg of insulin
     */
    fun getInsulin(insulinLabel: String): ICfg = iCfg

    /**
     * provide the first iCfg available within insulin with selected concentration
     * with Current running concentration iCfg will be returned
     * with another concentration (standard call when you change concentration, it provides the first insulin in the list)
     */
    fun getDefaultInsulin(concentration: Double? = null): ICfg = iCfg

    val comment: String

    /**
     * Provide Current Pump Insulin
     */
    val iCfg: ICfg
}