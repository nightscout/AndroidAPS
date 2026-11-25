package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalAapsScale
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
    // No profile is a problem and keeps its fill. A changed profile (a percentage, a time shift, a
    // temporary profile) is a choice: the icon in the in-progress colour, the name in the normal text
    // colour, and a shade for the time passed when it has an end, see chipShade
    val containerColor = if (isNoProfile) MaterialTheme.colorScheme.errorContainer else Color.Transparent
    val contentColor = if (isNoProfile) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = when {
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
            .height(AapsSpacing.chipHeight * LocalAapsScale.current)
    ) {
        Box(modifier = Modifier.fillMaxSize().chipShade(progress)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
            ) {
                Icon(
                    imageVector = ElementType.PROFILE_MANAGEMENT.icon(),
                    contentDescription = stringResourceOrNull(ElementType.PROFILE_MANAGEMENT.label()),
                    tint = accentColor,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                // One line: the chip has a fixed height, so a second line was cut in half. The weight
                // keeps the scene badge on the chip when the name is long.
                Text(
                    text = profileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(start = AapsSpacing.medium)
                )
                if (sceneManaged) {
                    SceneBadge(modifier = Modifier.padding(start = AapsSpacing.small))
                }
            }
        }
    }
}
