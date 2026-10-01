package app.aaps.core.interfaces.pump

/**
 * This class represents basal insulin delivered by pump.
 *
 * Example: when using U20 insulin and user request 0.6U/h insulin,
 * pump should deliver 0.6 * (100 / 20) = 3.0U/h.
 * In this case pump driver must use PumpRate(3.0) which will be translated
 * by PumpSync back to 0.6U/h to store in database.
 * For relative basal rate (i.e. %) no conversion is done
 */
class PumpRate(val cU: Double) {

    /**
     * Convert basal insulin delivered by pump to U100 units
     * For relative basal rate (i.e. %) no conversion is done
     * @param concentration Insulin concentration (0.2 for U20, 2.0 for U200 insulin)
     * @return Basal insulin recalculated to U100 units used inside core of AAPS
     */
    fun iU(concentration: Double, isAbsolute: Boolean): Double =
        if (isAbsolute) cU * concentration
        else cU

    override fun equals(other: Any?): Boolean =
        if (other is PumpRate?) cU == other?.cU
        else false

    override fun hashCode(): Int = (cU * 10000).toInt()

    override fun toString(): String = "PumpRate($cU)"
}