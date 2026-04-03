package app.aaps.ui.compose.overview.chips

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.ui.compose.overview.graphs.CobUiState

@Composable
internal fun CobChip(
    state: CobUiState,
    showIcon: Boolean = true,
    modifier: Modifier = Modifier
) {
    val alphaModifier = if (state.carbsReq > 0) {
        val infiniteTransition = rememberInfiniteTransition(label = "cobBlink")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cobAlpha"
        )
        Modifier.alpha(alpha)
    } else {
        Modifier
    }

    val hasValue = state.cobValue != 0.0
    Surface(
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = if (hasValue) ElementType.COB.color().copy(alpha = 0.2f) else Color.Transparent,
        modifier = modifier
            .heightIn(min = AapsSpacing.chipHeight)
            .then(alphaModifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
        ) {
            if (showIcon) {
                Icon(
                    imageVector = ElementType.COB.icon(),
                    contentDescription = null,
                    tint = ElementType.COB.color(),
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
            }
            Text(
                text = state.text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = if (showIcon) AapsSpacing.medium else 0.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CobChipPreview() {
    MaterialTheme {
        CobChip(state = CobUiState(text = "24g", cobValue = 24.0))
    }
}

@Preview(showBackground = true)
@Composable
private fun CobChipZeroPreview() {
    MaterialTheme {
        CobChip(state = CobUiState(text = "0g", cobValue = 0.0))
    }
}

@Preview(showBackground = true)
@Composable
private fun CobChipBlinkingPreview() {
    MaterialTheme {
        CobChip(state = CobUiState(text = "12g\n45 required", carbsReq = 45, cobValue = 12.0))
    }
}
