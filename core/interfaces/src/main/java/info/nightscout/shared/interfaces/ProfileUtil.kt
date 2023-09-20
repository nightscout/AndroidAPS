package info.nightscout.shared.interfaces

import info.nightscout.interfaces.GlucoseUnit

interface ProfileUtil {

    /**
     * Units selected in [info.nightscout.shared.sharedPreferences.SP]
     *
     * @return GlucoseUnit for UI
     */
    val units: GlucoseUnit

    /**
     * Convert value from mg/dl to selected units
     *
     * @param valueInMgdl glucose value in mgdl
     * @param targetUnits target units
     * @return value in target units
     */
    fun fromMgdlToUnits(valueInMgdl: Double, targetUnits: GlucoseUnit = units): Double

    /**
     * Convert value from mmol/l to selected units
     *
     * @param value glucose value in mmol/l
     * @param targetUnits target units
     * @return value in target units
     */
    fun fromMmolToUnits(value: Double, targetUnits: GlucoseUnit = units): Double

    /**
     * Convert to currently used units
     *
     * @param anyBg glycemia
     * @return value in mg/dl or mmol/l
     */
    fun valueInCurrentUnitsDetect(anyBg: Double): Double

    /**
     * Detect units of [anyBg] and return string in currently used units.
     * Values >= 39 are expected to be in mmol/l, below in mg/dl.
     *
     * @param anyBg value either in mmol/l or mg/dl
     * @return formatted string in current units
     */
    fun stringInCurrentUnitsDetect(anyBg: Double): String

    /**
     * Value based on [targetUnits] parameter as a formatted string
     *
     * @param valueInMgdl known value in mg/dl
     * @param targetUnits wanted units
     * @return formatted one of the values based on [targetUnits] parameter
     */
    fun fromMgdlToStringInUnits(valueInMgdl: Double?, targetUnits: GlucoseUnit = units): String

    /**
     * Pick from values based on [targetUnits] parameter as a formatted string with +/- sign
     *
     * @param valueInMgdl known value in mg/dl
     * @param targetUnits wanted units
     * @return formatted one of the values based on [targetUnits] parameter
     */
    fun fromMgdlToSignedStringInUnits(valueInMgdl: Double, targetUnits: GlucoseUnit = units): String

    /**
     * Test if value is in mg/dl.
     *
     * @param anyBg glycemia
     * @return true if value < 39
     */
    fun isMgdl(anyBg: Double): Boolean

    /**
     * Test if value is in mmol/l
     *
     * @param anyBg glycemia
     * @return true if value >= 39
     */
    fun isMmol(anyBg: Double): Boolean

    /**
     * Detect units of [anyBg]
     *
     * @param anyBg glycemia
     * @return [GlucoseUnit.MMOL] if value >= 39 otherwise [GlucoseUnit.MGDL]
     */
    fun unitsDetect(anyBg: Double): GlucoseUnit

    /**
     * Convert to selected units
     *
     * @param anyBg glycemia
     * @param targetUnits target units
     * @return value in mg/dl or mmol/l
     */
    fun valueInUnitsDetect(anyBg: Double, targetUnits: GlucoseUnit): Double

    /**
     * Detect units of [anyBg] and return string in [targetUnits]
     * Values >= 39 are expected to be in mmol/l, below in mg/dl.
     *
     * @param anyBg value either in mmol/l or mg/dl
     * @return formatted string
     */
    fun stringInUnitsDetect(anyBg: Double, targetUnits: GlucoseUnit): String

    /**
     * Detect units and convert value to mg/dl
     * Values >= 39 are expected to be in mmol/l, below in mg/dl.
     *
     * @param anyBg in any units
     * @return value in mg/dl
     */
    fun convertToMgdlDetect(anyBg: Double): Double

    /**
     * Convert value to mg/dl
     *
     * @param value value in [sourceUnits]
     * @return value in mg/dl
     */
    fun convertToMgdl(value: Double, sourceUnits: GlucoseUnit): Double

    /**
     * Convert value to mmol/l
     *
     * @param value value in [sourceUnits]
     * @return value in mmol/l
     */
    fun convertToMmol(value: Double, sourceUnits: GlucoseUnit): Double

    /**
     * Create properly formatted string of range "low - high".
     * Targets are stored in mg/dl but profile vary
     *
     * @param low low range value
     * @param high high range value
     * @param sourceUnits units of source values
     * @param targetUnits target units for formatting
     * @return formatted range string
     */
    //
    fun toTargetRangeString(low: Double, high: Double, sourceUnits: GlucoseUnit, targetUnits: GlucoseUnit = units): String
}