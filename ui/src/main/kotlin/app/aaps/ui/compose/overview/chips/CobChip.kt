package app.aaps.ui.compose.overview.chips

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ExcludeFromJacocoGeneratedReport
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CobChip(
    state: CobUiState,
    showIcon: Boolean = true,
    modifier: Modifier = Modifier
) {
    // When carbs are required, flash only the icon (attention cue) and let the text scroll
    // (basicMarquee) instead of wrapping — so the numbers stay crisp/readable while the icon blinks.
    val iconAlphaModifier = if (state.carbsReq > 0) {
        val infiniteTransition = rememberInfiniteTransition(label = "cobBlink")
        val alphaState = infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cobAlpha"
        )
        Modifier.graphicsLayer { alpha = alphaState.value }
    } else {
        Modifier
    }

    val hasValue = state.cobValue != 0.0
    Surface(
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = if (hasValue) ElementType.COB.color().copy(alpha = 0.2f) else Color.Transparent,
        modifier = modifier
            .heightIn(min = AapsSpacing.chipHeight)
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
                    modifier = Modifier
                        .size(AapsSpacing.chipIconSize)
                        .then(iconAlphaModifier)
                )
            }
            Text(
                text = state.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = if (showIcon) AapsSpacing.medium else 0.dp)
                    .basicMarquee()
            )
        }
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun CobChipPreview() {
    MaterialTheme {
        CobChip(state = CobUiState(text = "24g", cobValue = 24.0))
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun CobChipZeroPreview() {
    MaterialTheme {
        CobChip(state = CobUiState(text = "0g", cobValue = 0.0))
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun CobChipBlinkingPreview() {
    MaterialTheme {
        CobChip(state = CobUiState(text = "12g 45req", carbsReq = 45, cobValue = 12.0))
    }
}
