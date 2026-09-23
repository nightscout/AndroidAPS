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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource

/**
 * @see IobChipPreview
 * @see IobChipZeroPreview
 */
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
        // Name the whole chip, not the icon. state.text is only a bare value like "1.20 U", so
        // without a noun a screen reader cannot tell it from a bolus. It goes on the Surface and
        // not on the Icon for two reasons: the icon is dropped when the row is too narrow
        // (see IobCobChips), and a clickable Surface merges its children, so a description set
        // here replaces the value text and has to carry it.
        val chipDescription = stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.iob), state.text)
        Surface(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
            color = if (hasValue) ElementType.INSULIN.color().copy(alpha = 0.2f) else Color.Transparent,
            modifier = modifier
                .heightIn(min = AapsSpacing.chipHeight)
                .semantics { contentDescription = chipDescription }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
            ) {
                if (showIcon) {
                    Icon(
                        imageVector = ElementType.INSULIN.icon(),
                        // Decorative: the Surface above names the chip and carries the value.
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
