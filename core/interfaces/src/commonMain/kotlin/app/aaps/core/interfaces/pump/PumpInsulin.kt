package app.aaps.core.interfaces.pump

/**
 * This class represents insulin amount delivered by pump.
 *
 * Example: when using U20 insulin and user request 0.6U insulin,
 * pump should deliver 0.6 * (100 / 20) = 3.0U.
 * In this case pump driver must use PumpInsulin(3.0) which will be translated
 * by PumpSync back to 0.6U to store in database
 */
class PumpInsulin(val cU: Double) {

    /**
     * Convert amount of insulin delivered by pump to U100 units
     * @param concentration Insulin concentration (0.2 for U20, 2.0 for U200 insulin)
     * @return Insulin amount recalculated to U100 units used inside core of AAPS
     */
    fun iU(concentration: Double): Double = cU * concentration

    override fun equals(other: Any?): Boolean =
        if (other is PumpInsulin?) cU == other?.cU
        else false

    override fun hashCode(): Int = (cU * 10000).toInt()

    override fun toString(): String = "PumpInsulin($cU)"
}