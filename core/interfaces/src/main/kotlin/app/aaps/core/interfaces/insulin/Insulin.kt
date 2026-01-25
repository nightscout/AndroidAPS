package app.aaps.core.interfaces.insulin

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.configuration.ConfigExportImport

interface Insulin : ConfigExportImport {

    val id: InsulinType
    val friendlyName: String

    /**
     * Provide list of available Insulin
     * with no parameter (concentration == null) or without enable_insulin_concentration, only Insulins using the current running concentration will be provided (by default 1.0)
     * if concentration is provided with 0.0, then the overall list of available insulins is provided (external boluses provided by pens)
     * if a specific concentration is provided, then only the list of insulin consistent with selected value will be provided (to manage changing concentration value during Insulin_Change events)
     *
     * @param concentration only provide the list of Insulins using a specific concentration value
     * @return Basal insulin recalculated to U100 units used inside core of AAPS
     */
    fun insulinList(concentration: Double? = null): List<CharSequence> = emptyList()

    /**
     * provide insulin configuration with insulinLabel,
     * if insulinLabel is not found within insulins, then current running iCfg is returned
     *
     * @param insulinLabel insulinLabel of insulin
     * @return ICfg of insulin
     */
    fun getInsulin(insulinLabel: String): ICfg = iCfg

    /**
     * provide the first iCfg available within insulins with selected concentration
     * with Current running concentration iCfg will be returned
     * with another concentration (standard call when you change concentration, it provides the first insulin in the list)
     */
    fun getDefaultInsulin(concentration: Double? = null): ICfg = iCfg

    /**
     * Call done to approve a new concentration
     * triggered by ConcentrationDialog on confirmation of a non standard concentration different to U100
     * or by ProfileSwitch dialog when a new concentration has been selected (ConcentrationDialog->InsulinSwitchDialog->ProfileSwitchDialog)
     *
     * @param concentration new concentration
     */
    fun approveConcentration(concentration: Double) {}

    val comment: String
    @Deprecated("Use iCfg.dia instead")
    val dia: Double
        get() = iCfg.dia
    @Deprecated("Use iCfg.peak instead")
    val peak: Int
        get() = iCfg.peak

    /**
     * Provide Current Pump Insulin
     */
    val iCfg: ICfg
}