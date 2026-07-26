package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ExcludeFromJacocoGeneratedReport
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon

@Composable
internal fun IobChip(
    state: IobUiState,
    onClick: () -> Unit,
    showIcon: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hasValue = state.iobTotal != 0.0
    val haptic = LocalHapticFeedback.current
    // Disable the clickable Surface's 48dp minimum interactive size so the chip keeps its
    // compact height and stays vertically aligned with the (non-clickable) CobChip.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
            color = if (hasValue) ElementType.INSULIN.color().copy(alpha = 0.2f) else Color.Transparent,
            modifier = modifier.heightIn(min = AapsSpacing.chipHeight)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
            ) {
                if (showIcon) {
                    Icon(
                        imageVector = ElementType.INSULIN.icon(),
                        contentDescription = null,
                        tint = ElementType.INSULIN.color(),
                        modifier = Modifier.size(AapsSpacing.chipIconSize)
                    )
                }
                Text(
                    text = state.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = if (showIcon) AapsSpacing.medium else 0.dp)
                )
            }
        }
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun IobChipPreview() {
    MaterialTheme {
        IobChip(state = IobUiState(text = "1.25 U", iobTotal = 1.25), onClick = {})
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun IobChipZeroPreview() {
    MaterialTheme {
        IobChip(state = IobUiState(text = "0.00 U", iobTotal = 0.0), onClick = {})
    }
}
