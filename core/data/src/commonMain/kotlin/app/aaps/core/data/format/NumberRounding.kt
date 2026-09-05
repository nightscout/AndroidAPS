package app.aaps.core.data.format

/**
 * What to do with a value that sits exactly halfway between two renderable ones.
 *
 * Only reachable when the halfway point is exactly representable as a `Double`, which is why the
 * choice matters far less often than it looks. Rounding to whole numbers has reachable ties, because
 * `x.5` is a dyadic rational. Rounding to one decimal does not: a tie there would have to be
 * `(2k+1)/20`, and the factor of 5 in the denominator means no `Double` ever lands on it.
 */
enum class NumberRounding {

    /**
     * Ties go to the even neighbour: `0.5` renders as `0`, `1.5` as `2`.
     *
     * Banker's rounding. It exists to stop a bias from building up when many rounded values are
     * summed, and it is the default because it is what `DecimalFormat` has always done here.
     */
    HALF_EVEN,

    /**
     * Ties go away from zero: `0.5` renders as `1`.
     *
     * What a reader expects from a single number on screen, so it is the right choice for a value
     * shown on its own rather than one that will be added up.
     */
    HALF_UP
}
