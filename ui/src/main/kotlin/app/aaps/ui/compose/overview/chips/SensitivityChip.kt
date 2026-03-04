package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcArrowFlat
import app.aaps.core.ui.compose.icons.IcArrowFortyfiveDown
import app.aaps.core.ui.compose.icons.IcArrowFortyfiveUp
import app.aaps.core.ui.compose.icons.IcAs
import app.aaps.core.ui.compose.icons.IcAsAbove
import app.aaps.core.ui.compose.icons.IcAsAboveX
import app.aaps.core.ui.compose.icons.IcAsBelow
import app.aaps.core.ui.compose.icons.IcAsBelowX
import app.aaps.core.ui.compose.icons.IcAsX
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.ui.compose.overview.graphs.SensitivityUiState

@Composable
internal fun SensitivityChip(
    state: SensitivityUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = selectSensIcon(ratio = state.ratio, isEnabled = state.isEnabled)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = ElementType.SENSITIVITY.color().copy(alpha = 0.15f),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        val chipStyle = AapsTheme.typography.chipLabel
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElementType.SENSITIVITY.color(),
                modifier = Modifier.size(18.dp)
            )
            if (state.asText.isNotEmpty()) {
                Text(
                    text = state.asText,
                    style = chipStyle,
                    color = textColor
                )
            }
            if (state.asText.isNotEmpty() && state.isfFrom.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
            }
            if (state.isfFrom.isNotEmpty()) {
                Text(
                    text = state.isfFrom,
                    style = chipStyle,
                    color = textColor
                )
                Icon(
                    imageVector = selectIsfArrow(state.isfFrom, state.isfTo),
                    contentDescription = null,
                    tint = ElementType.SENSITIVITY.color(),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = state.isfTo,
                    style = chipStyle,
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

private fun selectIsfArrow(fromStr: String, toStr: String): ImageVector {
    val from = fromStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val to = toStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    return when {
        to > from -> IcArrowFortyfiveUp
        to < from -> IcArrowFortyfiveDown
        else      -> IcArrowFlat
    }
}

@Preview(showBackground = true)
@Composable
private fun SensitivityChipAbovePreview() {
    MaterialTheme {
        SensitivityChip(
            state = SensitivityUiState(asText = "112%", isfFrom = "5.5", isfTo = "6.8", ratio = 1.12, isEnabled = true, hasData = true),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SensitivityChipBelowDisabledPreview() {
    MaterialTheme {
        SensitivityChip(
            state = SensitivityUiState(asText = "88%", ratio = 0.88, isEnabled = false, hasData = true),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SensitivityChipIsfDownPreview() {
    MaterialTheme {
        SensitivityChip(
            state = SensitivityUiState(isfFrom = "6.0", isfTo = "4.2", ratio = 1.15, isEnabled = true, hasData = true),
            onClick = {}
        )
    }
}
