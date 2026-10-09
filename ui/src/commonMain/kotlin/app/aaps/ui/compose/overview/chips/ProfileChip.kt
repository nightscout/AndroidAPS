package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResourceOrNull

/**
 * @see ProfileChipPreview
 * @see ProfileChipModifiedPreview
 */
@Composable
fun ProfileChip(
    profileName: String,
    isModified: Boolean,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sceneManaged: Boolean = false,
    isNoProfile: Boolean = false,
    enabled: Boolean = true
) {
    val containerColor = when {
        isNoProfile -> MaterialTheme.colorScheme.errorContainer
        isModified  -> AapsTheme.generalColors.inProgress.copy(alpha = 0.2f)
        else        -> Color.Transparent
    }
    val contentColor = when {
        isNoProfile -> MaterialTheme.colorScheme.onErrorContainer
        isModified  -> AapsTheme.generalColors.inProgress
        else        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val haptic = LocalHapticFeedback.current

    Surface(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .height(AapsSpacing.chipHeight)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
            ) {
                Icon(
                    imageVector = ElementType.PROFILE_MANAGEMENT.icon(),
                    contentDescription = stringResourceOrNull(ElementType.PROFILE_MANAGEMENT.label()),
                    tint = contentColor,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                // One line: the chip has a fixed height, so a second line was cut off. When the name plus
                // "(110%) (2h 40')" does not fit, the text shrinks a few steps first and only then gets an
                // ellipsis - a short name with a temporary switch, the common case, stays fully readable.
                // weight(1f) keeps the scene badge on screen.
                val textStyle = MaterialTheme.typography.bodyMedium
                Text(
                    text = profileName,
                    style = textStyle,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = MaterialTheme.typography.labelSmall.fontSize,
                        maxFontSize = textStyle.fontSize,
                        stepSize = 1.sp
                    ),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(start = AapsSpacing.medium)
                )
                if (sceneManaged) {
                    SceneBadge(modifier = Modifier.padding(start = AapsSpacing.small))
                }
            }
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(AapsSpacing.chipProgressHeight),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.3f)
                )
            }
        }
    }
}
