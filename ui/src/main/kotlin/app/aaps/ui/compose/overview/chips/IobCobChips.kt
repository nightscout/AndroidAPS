package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import app.aaps.core.ui.compose.AapsSpacing

/**
 * @see IobCobChipsRowPreview
 * @see IobCobChipsRowCarbsReqPreview
 */
@Composable
fun IobCobChipsRow(
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    onIobChipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacingDp = AapsSpacing.small
    SubcomposeLayout(
        modifier = modifier.fillMaxWidth()
    ) { constraints ->
        val spacingPx = spacingDp.roundToPx()
        val isWidthBounded = constraints.hasBoundedWidth
        val availableWidth = if (isWidthBounded) (constraints.maxWidth - spacingPx).coerceAtLeast(0) else 0

        // First pass: measure intrinsic widths with icons
        val withIcons = subcompose("withIcons") {
            IobChip(state = iobUiState, onClick = onIobChipClick, showIcon = true)
            CobChip(state = cobUiState, showIcon = true)
        }
        val intrinsicsWithIcons = withIcons.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val totalWithIcons = intrinsicsWithIcons.sum()

        // If chips with icons don't fit, hide icons to free up space
        val showIcons = if (isWidthBounded) totalWithIcons <= availableWidth else true

        val measurables = if (showIcons) {
            withIcons
        } else {
            subcompose("withoutIcons") {
                IobChip(state = iobUiState, onClick = onIobChipClick, showIcon = false)
                CobChip(state = cobUiState, showIcon = false)
            }
        }

        // Max intrinsic width = the width each chip needs to render on a single line.
        // (min intrinsic only guarantees the longest word fits, which wraps IOB.)
        val naturalWidths = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }

        val placeables = if (isWidthBounded) {
            // IOB hugs its content but never grabs more than half the row when space is tight.
            val iobWidth = naturalWidths[0].coerceAtMost(availableWidth / 2)
            // COB fills the rest of the row up to the edge so the chips span the full width;
            // its text marquee-scrolls when it can't fit (see CobChip).
            val cobWidth = (availableWidth - iobWidth).coerceAtLeast(0)
            listOf(
                measurables[0].measure(constraints.copy(minWidth = iobWidth, maxWidth = iobWidth)),
                measurables[1].measure(constraints.copy(minWidth = cobWidth, maxWidth = cobWidth))
            )
        } else {
            measurables.mapIndexed { i, measurable ->
                measurable.measure(constraints.copy(minWidth = naturalWidths[i], maxWidth = naturalWidths[i]))
            }
        }

        val height = if (placeables.isEmpty()) 0 else placeables.maxOf { it.height }
        val layoutWidth = if (isWidthBounded) constraints.maxWidth else placeables.sumOf { it.width } + (if (placeables.size > 1) (placeables.size - 1) * spacingPx else 0)
        layout(layoutWidth, height) {
            var x = 0
            placeables.forEachIndexed { i, placeable ->
                placeable.place(x, 0)
                x += placeable.width + if (i < placeables.lastIndex) spacingPx else 0
            }
        }
    }
}
