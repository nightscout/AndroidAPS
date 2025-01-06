package info.nightscout.comboctl.main

import info.nightscout.comboctl.base.toStringWithDecimal

const val NUM_COMBO_BASAL_PROFILE_FACTORS = 24

/**
 * Class containing the 24 basal profile factors.
 *
 * The factors are stored as integer-encoded-decimals. The
 * last 3 digits of the integers make up the fractional portion.
 * For example, integer factor 4100 actually means 4.1 IU.
 *
 * The Combo uses the following granularity:
 *   0.00 IU to 0.05 IU  : increment in 0.05 IU steps
 *   0.05 IU to 1.00 IU  : increment in 0.01 IU steps
 *   1.00 IU to 10.00 IU : increment in 0.05 IU steps
 *   10.00 IU and above  : increment in 0.10 IU steps
 *
 * The [sourceFactors] argument must contain exactly 24
 * integer-encoded-decimals. Any other amount will result
 * in an [IllegalArgumentException]. Furthermore, all
 * factors must be >= 0.
 *
 * [sourceFactors] is not taken as a reference. Instead,
 * its 24 factors are copied into an internal list that
 * is accessible via the [factors] property. If the factors
 * from [sourceFactors] do not match the granularity mentioned
 * above, they will be rounded before they are copied into
 * the [factors] property. It is therefore advisable to
 * look at that property after creating an instance of
 * this class to see what the profile's factors that the
 * Combo is using actually are like.
 *
 * @param sourceFactors The source for the basal profile's factors.
 * @throws IllegalArgumentException if [sourceFactors] does not
 *   contain exactly 24 factors or if at least one of these
 *   factors is negative.
 */
class BasalProfile(sourceFactors: List<Int>) {
    private val _factors = MutableList(NUM_COMBO_BASAL_PROFILE_FACTORS) { 0 }

    /**
     * Number of basal profile factors (always 24).
     *
     * This mainly exists to make this class compatible with
     * code that operates on collections.
     */
    val size = NUM_COMBO_BASAL_PROFILE_FACTORS

    /**
     * List with the basal profile factors.
     *
     * These are a copy of the source factors that were
     * passed to the constructor, rounded if necessary.
     * See the [BasalProfile] documentation for details.
     */
    val factors: List<Int> = _factors

    init {
        require(sourceFactors.size == _factors.size)

        sourceFactors.forEachIndexed { index, factor ->
            require(factor >= 0) { "Source factor #$index has invalid negative value $factor" }

            val granularity = when (factor) {
                in 0..50 -> 50
                in 50..1000 -> 10
                in 1000..10000 -> 50
                else -> 100
            }

            // Round the factor with integer math
            // to conform to the Combo granularity.
            _factors[index] = ((factor + granularity / 2) / granularity) * granularity
        }
    }

    override fun toString() = factors.mapIndexed { index, factor ->
        "hour $index: factor ${factor.toStringWithDecimal(3)}"
    }.joinToString("; ")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        other as BasalProfile
        return factors == other.factors
    }

    override fun hashCode(): Int {
        return factors.hashCode()
    }

    operator fun get(index: Int) = factors[index]

    operator fun iterator() = factors.iterator()
}
