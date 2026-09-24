package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.icons.IcArrowFlat
import app.aaps.core.ui.compose.icons.IcArrowFortyfiveDown
import app.aaps.core.ui.compose.icons.IcArrowFortyfiveUp
import app.aaps.core.ui.compose.icons.IcAs
import app.aaps.core.ui.compose.icons.IcAsAbove
import app.aaps.core.ui.compose.icons.IcAsAboveX
import app.aaps.core.ui.compose.icons.IcAsBelow
import app.aaps.core.ui.compose.icons.IcAsBelowX
import app.aaps.core.ui.compose.icons.IcAsX
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull

/**
 * @see SensitivityChipAbovePreview
 * @see SensitivityChipBelowDisabledPreview
 * @see SensitivityChipIsfDownPreview
 */
@Composable
internal fun SensitivityChip(
    state: SensitivityUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = selectSensIcon(ratio = state.ratio, isEnabled = state.isEnabled)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Autosens being switched off is drawn as a small X across the icon and nothing else, while the
    // chip goes on showing a percentage. So a screen reader read out a figure that is not actually
    // being applied, with no sign of it. Above or below profile stays unspoken on purpose - the
    // percentage itself already says which way it goes.
    val autosensOff = stringResource(CoreUiStrings.autosens_state_off)
    Surface(
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = ElementType.SENSITIVITY.color().copy(alpha = 0.2f),
        modifier = modifier
            .heightIn(min = AapsSpacing.chipHeight)
            .clickable(onClick = onClick)
            .then(
                if (!state.isEnabled) Modifier.semantics { stateDescription = autosensOff }
                else Modifier
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResourceOrNull(ElementType.SENSITIVITY.label()),
                tint = ElementType.SENSITIVITY.color(),
                modifier = Modifier.size(AapsSpacing.chipIconSize)
            )
            if (state.asText.isNotEmpty()) {
                Text(
                    text = state.asText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.padding(start = AapsSpacing.medium)
                )
            }
            if (state.asText.isNotEmpty() && state.isfFrom.isNotEmpty()) {
                Spacer(Modifier.width(AapsSpacing.medium))
            }
            if (state.isfFrom.isNotEmpty()) {
                Text(
                    text = state.isfFrom,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                Icon(
                    imageVector = selectIsfArrow(state.isfFrom, state.isfTo),
                    contentDescription = stringResourceOrNull(selectIsfArrowLabel(state.isfFrom, state.isfTo)),
                    tint = ElementType.SENSITIVITY.color(),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = state.isfTo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
        }
    }
}

private fun selectSensIcon(ratio: Double, isEnabled: Boolean): ImageVector =
    if (isEnabled) {
        when {
            ratio > 1.0 -> IcAsAbove
            ratio < 1.0 -> IcAsBelow
            else        -> IcAs
        }
    } else {
        when {
            ratio > 1.0 -> IcAsAboveX
            ratio < 1.0 -> IcAsBelowX
            else        -> IcAsX
        }
    }

/** Direction of the step between the two ISF values: 1 when it goes up, -1 when it goes down, 0 when it stays. */
private fun isfDirection(fromStr: String, toStr: String): Int {
    val from = fromStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val to = toStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    return when {
        to > from -> 1
        to < from -> -1
        else      -> 0
    }
}

private fun selectIsfArrow(fromStr: String, toStr: String): ImageVector =
    when (isfDirection(fromStr, toStr)) {
        1    -> IcArrowFortyfiveUp
        -1   -> IcArrowFortyfiveDown
        else -> IcArrowFlat
    }

/**
 * What the arrow between the two ISF values MEANS, which is that the value rises or falls - not
 * which way the glyph happens to point. Naming the glyph gave "5.5, Up Right, 6.8", and for two
 * equal values "5.5, Right, 5.5", which says nothing at all.
 *
 * Equal returns null on purpose: two identical numbers already say the value did not change, and
 * every word available here would either be wrong ("Right") or borrowed from the glucose trend
 * vocabulary ("stable"), which is a different thing.
 */
private fun selectIsfArrowLabel(fromStr: String, toStr: String): TextRef? =
    when (isfDirection(fromStr, toStr)) {
        1    -> CoreUiStrings.arrow_up
        -1   -> CoreUiStrings.arrow_down
        else -> null
    }
