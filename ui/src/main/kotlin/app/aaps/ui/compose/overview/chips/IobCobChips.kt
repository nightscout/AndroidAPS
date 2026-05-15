package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.ui.compose.overview.graphs.CobUiState
import app.aaps.ui.compose.overview.graphs.IobUiState

@Composable
fun IobCobChipsRow(
    iobUiState: IobUiState,
    cobUiState: CobUiState,
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
            IobChip(state = iobUiState, showIcon = true)
            CobChip(state = cobUiState, showIcon = true)
        }
        val intrinsicsWithIcons = withIcons.map { it.minIntrinsicWidth(constraints.maxHeight) }
        val totalWithIcons = intrinsicsWithIcons.sum()

        // If chips with icons don't fit, hide icons to free up space
        val showIcons = if (isWidthBounded) totalWithIcons <= availableWidth else true

        val measurables = if (showIcons) {
            withIcons
        } else {
            subcompose("withoutIcons") {
                IobChip(state = iobUiState, showIcon = false)
                CobChip(state = cobUiState, showIcon = false)
            }
        }

        val intrinsics = measurables.map { it.minIntrinsicWidth(constraints.maxHeight) }
        val totalIntrinsic = intrinsics.sum()

        // Scale each chip proportionally so they fill 100% of available space
        val placeables = measurables.mapIndexed { i, measurable ->
            val w = if (isWidthBounded) {
                if (totalIntrinsic > 0)
                    (intrinsics[i].toLong() * availableWidth / totalIntrinsic).toInt()
                else
                    availableWidth / measurables.size
            } else {
                intrinsics[i]
            }
            measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
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

@Preview(showBackground = true)
@Composable
private fun IobCobChipsRowPreview() {
    MaterialTheme {
        IobCobChipsRow(
            iobUiState = IobUiState(text = "1.25 U", iobTotal = 1.25),
            cobUiState = CobUiState(text = "24g", cobValue = 24.0)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IobCobChipsRowCarbsReqPreview() {
    MaterialTheme {
        IobCobChipsRow(
            iobUiState = IobUiState(text = "1.25 U", iobTotal = 1.25),
            cobUiState = CobUiState(text = "12g\n45 required", carbsReq = 45, cobValue = 12.0)
        )
    }
}
