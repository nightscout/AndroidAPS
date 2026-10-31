package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.SeriesType

// The axes of a graph below BG, from a few rules that hold for every series:
// - the first series is drawn against the left axis, the second against the right one, unless the
//   two have the same unit and so share one axis ([graphSides]);
// - every series type has one axis rule, the same alone and in a pair ([axisRuleOf], [axisRange]);
// - two axes keep their own ranges, only their zeros are put on one height ([alignReferences]);
// - the IOB graph alone leaves the top of its height to the basal band ([graphAxes]).

/** How the axis of a series is built from its values. */
internal sealed interface AxisRule {

    /** From zero: amounts that are mostly positive (COB, IOB, deviations, ...). A negative part is kept. */
    data object ZeroFloor : AxisRule

    /** Centered on [pivot], the value that means "no change" (SENS' 100 %, DEV_SLOPE's 0). See [niceScaleAroundPivot]. */
    data class Pivot(val pivot: Double, val minDeviation: Double = 0.0) : AxisRule

    /** No fixed point: only the values decide the range (variable sensitivity, heart rate). */
    data object Free : AxisRule
}

internal fun axisRuleOf(type: SeriesType): AxisRule = when (type) {
    SeriesType.SENSITIVITY                            -> AxisRule.Pivot(100.0, SENS_MIN_DEVIATION)
    SeriesType.DEV_SLOPE                              -> AxisRule.Pivot(0.0)
    SeriesType.VAR_SENSITIVITY, SeriesType.HEART_RATE -> AxisRule.Free
    else                                              -> AxisRule.ZeroFloor
}

/** The series that are measured in the same unit, and so are drawn against one axis. */
private val SHARED_UNITS = listOf(
    setOf(SeriesType.IOB, SeriesType.ABS_IOB),
    setOf(SeriesType.BGI, SeriesType.DEVIATIONS)
)

/** The series of one graph, by axis. A side is empty when it has no series. */
internal data class GraphSides(val left: List<SeriesType>, val right: List<SeriesType>)

/** The first series on the left axis, the second on the right one, or on the left as well when the two have the same unit. */
internal fun graphSides(types: List<SeriesType>): GraphSides {
    val first = types.firstOrNull() ?: return GraphSides(emptyList(), emptyList())
    val second = types.getOrNull(1) ?: return GraphSides(listOf(first), emptyList())
    val sameUnit = SHARED_UNITS.any { first in it && second in it }
    return if (sameUnit) GraphSides(listOf(first, second), emptyList()) else GraphSides(listOf(first), listOf(second))
}

/**
 * The range of one axis: its values, rounded out by the rule of the series. [reference] is the value
 * the rule keeps on the axis (the zero, the pivot), null for a free range. The labels stay inside
 * [min]..[max] even where the drawn axis goes further.
 */
internal data class AxisRange(val min: Double, val max: Double, val reference: Double?) {

    val bounds: AxisBounds get() = AxisBounds(min, max)
}

/** The y range an axis is drawn with. */
internal data class AxisBounds(val min: Double, val max: Double)

/** The axis of a graph that has no values: a frame, so the grid and the now line still draw. */
internal val EMPTY_AXIS = AxisBounds(0.0, 1.0)

/** The range for [values] by [rule], or null when there are no values. */
internal fun axisRange(rule: AxisRule, values: List<Double>): AxisRange? {
    if (values.isEmpty()) return null
    val low = values.min()
    val high = values.max()
    return when (rule) {
        AxisRule.ZeroFloor -> zeroFloorNiceRange(low, high.coerceAtLeast(0.0)).let { AxisRange(it.min, it.max, 0.0) }
        is AxisRule.Pivot  -> niceScaleAroundPivot(low, high, rule.pivot, rule.minDeviation).let { AxisRange(it.min, it.max, rule.pivot) }
        AxisRule.Free      -> niceScale(low, high).let { AxisRange(it.min, it.max, null) }
    }
}

/**
 * Two axes with their references on one height: both zeros on one line, or a zero and SENS' 100 %.
 * Each reference sits at some fraction of its own axis; both go to the average of the two. The axis
 * whose reference sits lower is stretched down, the other one up, so nothing is ever cut off and
 * neither axis loses more than half of the difference. An axis without a reference is left alone.
 */
internal fun alignReferences(a: AxisRange, b: AxisRange): Pair<AxisBounds, AxisBounds> {
    val aReference = a.reference ?: return a.bounds to b.bounds
    val bReference = b.reference ?: return a.bounds to b.bounds
    val target = (a.fractionOf(aReference) + b.fractionOf(bReference)) / 2.0
    return a.stretchedTo(aReference, target) to b.stretchedTo(bReference, target)
}

private fun AxisRange.fractionOf(value: Double): Double = (value - min) / (max - min)

/** The bounds with [reference] at [target] of the height, reached by moving only one bound outwards. */
private fun AxisRange.stretchedTo(reference: Double, target: Double): AxisBounds {
    val fraction = fractionOf(reference)
    return when {
        target > fraction -> AxisBounds((reference - target * max) / (1.0 - target), max)
        target < fraction -> AxisBounds(min, min + (reference - min) / target)
        else              -> bounds
    }
}

/** The bounds a graph draws its axes with. [right] is null when the graph has no right axis with values. */
internal data class GraphAxes(val left: AxisBounds, val right: AxisBounds?)

/**
 * The drawn axes of a graph from the ranges of its two sides. With [basalBand], the left axis goes on
 * above its values, so they take the lower [IOB_HEIGHT_FRACTION] of the height and the basal band
 * has the top. The room is measured from the bottom of the values, not from zero: the two only
 * coincide when there is no negative part, and measured from zero the negative part would get less
 * than its share.
 */
internal fun graphAxes(left: AxisRange?, right: AxisRange?, basalBand: Boolean): GraphAxes = when {
    left == null  -> GraphAxes(EMPTY_AXIS, right?.bounds)
    basalBand     -> GraphAxes(AxisBounds(left.min, left.min + (left.max - left.min) / IOB_HEIGHT_FRACTION), null)
    right == null -> GraphAxes(left.bounds, null)
    else          -> alignReferences(left, right).let { (l, r) -> GraphAxes(l, r) }
}
