package app.aaps.ui.compose.overview.graphs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.interfaces.overview.graph.SeriesType
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

/**
 * The IOB and COB graphs draw no bolus and carbs markers. Instead the amount of each
 * entry stands over the step it makes in the curve: "1.3" over the jump the bolus makes in IOB, "30"
 * over the jump the carbs make in COB. The number is the only mark, and it says what the curve rose by.
 *
 * Every number is shown. Each one takes the first free place, see [placeLabels]: over the curve, then
 * the rows further up, then under the curve and the rows further down. eCarbs come every few minutes,
 * closer than a number is wide, and a number at the top of the graph has no room over the curve. The
 * SMB triangles of the IOB graph count as taken, see [AmountLabels.keepClear], so a number never
 * stands on one.
 *
 * Each number has a soft halo in the surface colour, see [amountLabelText], so it reads over the fill,
 * the failover dots and the basal band alike without a box of its own.
 *
 * A decoration, not Vico data labels: a data label formats the y value of its point, and gets no more
 * width than the gap to its neighbour, which turns eCarbs into "...". The labels travel with the
 * model under [AMOUNT_LABELS_KEY].
 */
internal class AmountLabelDecoration(
    private val insulin: TextComponent,
    private val carbs: TextComponent
) : Decoration {

    override fun drawOverLayers(context: CartesianDrawingContext) {
        val labels = context.model.extraStore.getOrNull(AMOUNT_LABELS_KEY) ?: return
        with(context) {
            if (ranges.xStep == 0.0) return
            val taken = labels.keepClear.mapNotNull { box ->
                val centerY = canvasY(box.y, box.axis) ?: return@mapNotNull null
                val centerX = canvasX(box.x)
                val half = box.size.pixels / 2
                LabelPlace(centerX - half, centerY - half, centerX + half, centerY + half)
            }
            drawRow(labels.insulin, insulin, taken)
            drawRow(labels.carbs, carbs, taken)
        }
    }

    private fun CartesianDrawingContext.drawRow(labels: List<AmountLabel>, component: TextComponent, taken: List<LabelPlace>) {
        // Labels come sorted by x, so the candidates are too
        val candidates = labels.mapNotNull { label ->
            if (label.text.isEmpty()) return@mapNotNull null
            // A treatment ahead of the curve stands at its amount, which can lie above the axis: then at the edge
            val centerY = (canvasY(label.y, label.axis) ?: return@mapNotNull null).coerceIn(layerBounds.top, layerBounds.bottom)
            val centerX = canvasX(label.x)
            if (centerX < layerBounds.left || centerX > layerBounds.right) return@mapNotNull null
            Candidate(label.text, centerX, centerY, component.getWidth(this, label.text) / 2)
        }
        if (candidates.isEmpty()) return
        // One text size, so one row height for the whole row
        val height = candidates.maxOf { component.getHeight(this, it.text) }
        val places = placeLabels(
            centersX = candidates.map { it.centerX },
            centersY = candidates.map { it.centerY },
            halfWidths = candidates.map { it.halfWidth },
            height = height,
            lift = LABEL_LIFT.pixels,
            gap = LABEL_GAP.pixels,
            top = layerBounds.top,
            bottom = layerBounds.bottom,
            maxRows = MAX_ROWS,
            taken = taken
        )
        for ((i, place) in places.withIndex()) {
            if (place == null) continue
            // The text ends at the bottom of its place
            component.draw(this, candidates[i].text, candidates[i].centerX, place.bottom, verticalPosition = Position.Vertical.Top)
        }
    }

    /** The canvas x of chart [x], with the scroll. */
    private fun CartesianDrawingContext.canvasX(x: Double): Float =
        layerBounds.left + layerDimensions.startPadding + layerDimensions.xSpacing * ((x - ranges.minX) / ranges.xStep).toFloat() - scroll

    /** The canvas y of [y] on [axis], null while the axis has no range. */
    private fun CartesianDrawingContext.canvasY(y: Double, axis: Axis.Position.Vertical): Float? {
        val yRange = ranges.getYRange(axis)
        if (yRange.length == 0.0) return null
        return layerBounds.bottom - ((y - yRange.minY) / yRange.length).toFloat() * layerBounds.height
    }

    private class Candidate(val text: String, val centerX: Float, val centerY: Float, val halfWidth: Float)
}

/** One label: [x] in chart x, [y] in the units of [axis], [text] already formatted. */
internal data class AmountLabel(val x: Double, val y: Double, val axis: Axis.Position.Vertical, val text: String)

/** A marker the labels keep clear of: a box [size] wide and high, centered on ([x], [y]) in the units of [axis]. */
internal data class MarkerBox(val x: Double, val y: Double, val axis: Axis.Position.Vertical, val size: Dp)

/** The labels of one graph, a row per kind, and the markers they keep clear of. */
internal data class AmountLabels(
    val insulin: List<AmountLabel>,
    val carbs: List<AmountLabel>,
    val keepClear: List<MarkerBox> = emptyList()
) {

    companion object {

        val EMPTY = AmountLabels(emptyList(), emptyList())
    }
}

/** The labels of the current model, for [AmountLabelDecoration]. */
internal val AMOUNT_LABELS_KEY = ExtraStore.Key<AmountLabels>()

/**
 * One size for every number, so a row of them reads as one row. A step under the axis numbers: those
 * set no size and Compose draws them at its 14 sp default, 12 looked too small next to them.
 */
private val AMOUNT_LABEL_FONT_SIZE = 13.sp

/** How far the halo in the surface colour spreads around each number. */
internal val AMOUNT_LABEL_HALO_BLUR = 3.dp

/** Space kept between two labels, side by side and between rows, and to a marker. */
private val LABEL_GAP = 2.dp

/** Space between the curve and the text. */
private val LABEL_LIFT = 2.dp

/**
 * Rows of labels tried over the curve, and again under it. Rows count from the label's own curve point,
 * so a label next to one on a higher point needs a few of them before it is clear; the top of the
 * graph stops the search anyway.
 */
private const val MAX_ROWS = 5

/** A curve point this far from the treatment, in minutes, still counts as its step. Two reading intervals. */
internal const val AMOUNT_LABEL_STEP_MAX = 10.0

/**
 * The text of the labels of one kind, in [color], with a soft halo in [surface] around it: a shadow
 * with no offset, [haloBlurPx] wide ([AMOUNT_LABEL_HALO_BLUR] in pixels, a shadow takes pixels).
 */
internal fun amountLabelText(color: Color, surface: Color, haloBlurPx: Float): TextComponent =
    TextComponent(
        textStyle = TextStyle(
            color = color,
            fontSize = AMOUNT_LABEL_FONT_SIZE,
            shadow = Shadow(color = surface, offset = Offset.Zero, blurRadius = haloBlurPx)
        )
    )

/** The axis [type] is drawn against on this graph. */
internal fun GraphSides.axisOf(type: SeriesType): Axis.Position.Vertical =
    if (type in right) Axis.Position.Vertical.End else Axis.Position.Vertical.Start

/**
 * The labels of one kind: every treatment (x, amount) of [treatments], at the curve's value at its
 * step ([curveValueAt]), with [format] of its amount. A treatment with no point of [curve] near it,
 * eCarbs still ahead of the now line or a gap in the data, stands at its own amount, where its marker
 * stood: the entry is there, it just has not gone into the curve. Sorted by x.
 */
internal fun amountLabelsOn(
    curve: List<Pair<Double, Double>>,
    treatments: List<Pair<Double, Double>>,
    axis: Axis.Position.Vertical,
    format: (Double) -> String
): List<AmountLabel> =
    treatments
        .map { (x, amount) -> AmountLabel(x, curveValueAt(x, curve) ?: amount, axis, format(amount)) }
        .sortedBy { it.x }

/**
 * The curve's value at the top of the step a treatment at [x] makes in it: the highest point from [x]
 * to [AMOUNT_LABEL_STEP_MAX] after it. The COB curve has a point at the treatment time that still
 * holds the old value, the jump comes with the next one, so the first point after is not enough. With
 * no point in that window, a gap in the data, the last point before [x] within the same distance.
 * Null for a treatment ahead of the curve's last point, it is not in the curve yet, and when no point
 * is near; see [amountLabelsOn] for what stands there then. [curve] is in ascending x order, (x, value).
 */
internal fun curveValueAt(x: Double, curve: List<Pair<Double, Double>>): Double? {
    val last = curve.lastOrNull() ?: return null
    if (x > last.first) return null
    val after = curve.filter { it.first >= x && it.first - x <= AMOUNT_LABEL_STEP_MAX }.maxOfOrNull { it.second }
    val before = curve.lastOrNull { it.first <= x && x - it.first <= AMOUNT_LABEL_STEP_MAX }
    return after ?: before?.second
}

/** A box on the canvas: the place of one label, or a marker the labels keep clear of. */
internal data class LabelPlace(val left: Float, val top: Float, val right: Float, val bottom: Float) {

    /** True when the two boxes come closer than [gap] to each other. */
    fun touches(other: LabelPlace, gap: Float): Boolean =
        left < other.right + gap && other.left < right + gap && top < other.bottom + gap && other.top < bottom + gap
}

/**
 * Where each label stands. For a label at ([centersX], [centersY]) with [halfWidths] and [height], the
 * places tried in order are: [lift] over the curve point, then [maxRows] rows further up a row height
 * plus [gap] apart, then [lift] under the point and the rows further down. The first place that lies
 * inside [top]..[bottom] and stays [gap] clear of every box in [taken] and of every label placed before
 * it wins. Null when no place does: more labels over each other than the rows hold, which is rare.
 *
 * Tried in x order, so of two that touch the earlier one gets the place nearer the curve.
 */
internal fun placeLabels(
    centersX: List<Float>,
    centersY: List<Float>,
    halfWidths: List<Float>,
    height: Float,
    lift: Float,
    gap: Float,
    top: Float,
    bottom: Float,
    maxRows: Int,
    taken: List<LabelPlace> = emptyList()
): List<LabelPlace?> {
    val placed = taken.toMutableList()
    val rowStep = height + gap
    return centersX.indices.map { i ->
        val left = centersX[i] - halfWidths[i]
        val right = centersX[i] + halfWidths[i]
        val over = (0 until maxRows).map { row ->
            val placeBottom = centersY[i] - lift - row * rowStep
            LabelPlace(left, placeBottom - height, right, placeBottom)
        }
        val under = (0 until maxRows).map { row ->
            val placeTop = centersY[i] + lift + row * rowStep
            LabelPlace(left, placeTop, right, placeTop + height)
        }
        val place = (over + under).firstOrNull { candidate ->
            candidate.top >= top && candidate.bottom <= bottom && placed.none { it.touches(candidate, gap) }
        }
        place?.also { placed += it }
    }
}
