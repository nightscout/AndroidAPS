package app.aaps.core.interfaces.insulin

import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate

interface ConcentrationHelper {

    /**
     *  return true if default concentration (U100) is set, false if another concentration has been approved by user
     */
    fun isU100(): Boolean

    /**
     * Convert concentrated amount received from pump to IU using current concentration (conversion managed within PumpInsulin)
     *
     * @param amount PumpInsulin
     * @return Double (concerted value in IU)
     */
    fun fromPump(amount: PumpInsulin, isPriming: Boolean = false): Double

    /**
     * Convert concentrated absolute rate received from pump to IU using current concentration (conversion managed within PumpRate)
     *
     * @param rate PumpRate
     * @return Double (concerted value in IU)
     */
    fun fromPump(rate: PumpRate): Double

    /**
     * Convert EffectiveProfile defined in IU to Profile (in CU) sent to pump with current concentration
     * TBC if needed
     */
    //fun toPump(profile: EffectiveProfile): Profile

    /**
     * basalrate with units in U/h if U100 (to be used within Pump driver only)
     * i.e. "0.6 U/h", and with both value if other concentration: i.e. for U200 "0.6 U/h (0.3 CU/h)"
     *
     * @param rate PumpRate
     * @return String with units (U100) or with both units if not U100
     */
    fun basalRateString(rate: PumpRate, isAbsolute: Boolean, decimals: Int = 2): String

    /** show bolus or reservoir level with units in U if U100 (to be used within Pump driver only)
     * i.e. "4 U", and with both value if other concentration: i.e. for U200 "4 U (2 CU)"
     *
     * @param amount PumpInsulin
     * @return String with units (U100) or with both units if not U100
     */
    fun insulinAmountString(amount: PumpInsulin): String

    /** show bolus level with units in U if U100 with time ago (to be used within Pump driver only)
     * i.e. "4 U 5 min ago", and with both value if other concentration: i.e. for U200 "4 U (2 CU) 5 min ago"
     *
     * @param amount PumpInsulin
     * @param ago String
     * @return String with units (U100) or with both units if not U100
     */
    fun insulinAmountAgoString(amount: PumpInsulin, ago: String): String

    /**
     * show insulinConcentration as a String i.e. "U100", "U200", ...
     * TBC if needed
     */
    fun insulinConcentrationString(): String

    /** show bolus with volume in µl
     * Dedicated to Prime/Fill Dialog (to show volume of fluid delivered)
     * i.e. "0.7 U (7.0 µl)"
     *
     * @param amount insulin amount in CU
     */
    fun bolusWithVolume(amount: Double): String

    /**
     * Show bolus with volume in µl after conversion due to concentration
     * Dedicated to Prime/Fill Dialog (to show volume of fluid delivered)
     * i.e. "1.4 U (7.0 µl)"
     *
     * @param amount insulin amount in IU
     */
    fun bolusWithConvertedVolume(amount: Double): String

    /**
     * show bolus Progress information (to be used within BolusProgressData.updateProgress)
     *
     * @param delivered PumpInsulin
     */
    fun bolusProgressString(delivered: PumpInsulin, isPriming: Boolean = false): String

    /**
     * show bolus Progress information for wear (to be used within BolusProgressData.updateProgress)
     *
     * @param delivered PumpInsulin
     * @param total Double
     */
    fun bolusProgressString(delivered: PumpInsulin, total: Double, isPriming: Boolean = false): String

    /**
     * Provide current concentration approved by user
     * For Safety and to provide a reminder after Reservoir Change, I stored approved concentration in preferences (but we can of course discuss and change the management)
     * this approved concentration (automatically provided for U100), is used here and not getProfile()?.iCfg?.concentration (which can be null) but replaced by 1.0 by default
     *
     * Use case I had in mind is a fresh install (with no selected profile by default) and a load preference including U200 from a previous phone,
     * but not allowed due to missing concentration_enable file
     * - profileFunction.getProfile()?.iCfg?.concentration will answer 1.0 or null (default values)
     * - using stored value in preferences, I get 2.0 previously approved, but not allowed so I can raise a warning or disable loop until user fix manually the problem
     * (approve U100 or put enable_concentration file)
     */
    val concentration: Double
}