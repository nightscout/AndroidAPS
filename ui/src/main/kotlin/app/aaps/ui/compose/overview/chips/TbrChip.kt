package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.ExcludeFromJacocoGeneratedReport
import app.aaps.core.ui.compose.icons.IcNoTbr
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.core.ui.compose.icons.IcTbrLow

@Composable
fun TbrChip(
    state: TbrState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = AapsTheme.elementColors.tempBasal
    val containerColor = iconColor.copy(alpha = 0.2f)
    val haptic = LocalHapticFeedback.current

    // Disable the clickable Surface's 48dp minimum interactive size so this content-width chip
    // isn't padded to 48dp and centered, which would inset it from the row's right edge
    // (same guard IobChip uses).
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
            color = containerColor,
            modifier = modifier.height(AapsSpacing.chipHeight)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = AapsSpacing.medium)
            ) {
                Icon(
                    imageVector = state.toIcon(),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
            }
        }
    }
}

private fun TbrState.toIcon(): ImageVector = when (this) {
    TbrState.HIGH -> IcTbrHigh
    TbrState.LOW  -> IcTbrLow
    TbrState.NONE -> IcNoTbr
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun TbrChipHighPreview() {
    MaterialTheme { TbrChip(state = TbrState.HIGH, onClick = {}) }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun TbrChipLowPreview() {
    MaterialTheme { TbrChip(state = TbrState.LOW, onClick = {}) }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun TbrChipNonePreview() {
    MaterialTheme { TbrChip(state = TbrState.NONE, onClick = {}) }
}
