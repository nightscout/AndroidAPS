package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.RM
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcLoopClosed
import app.aaps.core.ui.compose.icons.IcLoopDisabled
import app.aaps.core.ui.compose.icons.IcLoopDisconnected
import app.aaps.core.ui.compose.icons.IcLoopLgs
import app.aaps.core.ui.compose.icons.IcLoopOpen
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.core.ui.compose.icons.IcLoopSuperbolus

@Composable
fun RunningModeChip(
    mode: RM.Mode,
    text: String,
    progress: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isTemporary = mode.mustBeTemporary()
    val iconColor = mode.toColor()
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = if (isTemporary) iconColor.copy(alpha = 0.2f) else Color.Transparent
    val haptic = LocalHapticFeedback.current

    Surface(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .height(AapsSpacing.chipHeight)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
            ) {
                Icon(
                    imageVector = mode.toIcon(),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                Text(
                    text = text,
                    color = textColor,
                    modifier = Modifier.padding(start = AapsSpacing.medium)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AapsSpacing.chipProgressHeight)
            ) {
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AapsSpacing.chipProgressHeight),
                        color = iconColor,
                        trackColor = iconColor.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

/**
 * Extension to get theme color for RM.Mode
 */
@Composable
internal fun RM.Mode.toColor(): Color = when (this) {
    RM.Mode.CLOSED_LOOP       -> AapsTheme.generalColors.loopClosed
    RM.Mode.CLOSED_LOOP_LGS   -> AapsTheme.generalColors.loopLgs
    RM.Mode.OPEN_LOOP         -> AapsTheme.generalColors.loopOpened
    RM.Mode.DISABLED_LOOP     -> AapsTheme.generalColors.loopDisabled
    RM.Mode.SUPER_BOLUS       -> AapsTheme.generalColors.loopSuperBolus
    RM.Mode.DISCONNECTED_PUMP -> AapsTheme.generalColors.loopDisconnected
    RM.Mode.SUSPENDED_BY_PUMP,
    RM.Mode.SUSPENDED_BY_USER,
    RM.Mode.SUSPENDED_BY_DST  -> AapsTheme.generalColors.loopDisabled

    RM.Mode.RESUME            -> AapsTheme.generalColors.loopClosed
}

/**
 * Extension to get Compose icon for RM.Mode
 */
internal fun RM.Mode.toIcon(): ImageVector = when (this) {
    RM.Mode.CLOSED_LOOP       -> IcLoopClosed
    RM.Mode.CLOSED_LOOP_LGS   -> IcLoopLgs
    RM.Mode.OPEN_LOOP         -> IcLoopOpen
    RM.Mode.DISABLED_LOOP     -> IcLoopDisabled
    RM.Mode.SUPER_BOLUS       -> IcLoopSuperbolus
    RM.Mode.DISCONNECTED_PUMP -> IcLoopDisconnected
    RM.Mode.SUSPENDED_BY_PUMP,
    RM.Mode.SUSPENDED_BY_USER,
    RM.Mode.SUSPENDED_BY_DST  -> IcLoopPaused

    RM.Mode.RESUME            -> IcLoopClosed
}

@Preview(showBackground = true)
@Composable
private fun RunningModeChipClosedLoopPreview() {
    MaterialTheme {
        RunningModeChip(
            mode = RM.Mode.CLOSED_LOOP,
            text = "Closed Loop",
            progress = 0f
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RunningModeChipSuspendedPreview() {
    MaterialTheme {
        RunningModeChip(
            mode = RM.Mode.SUSPENDED_BY_USER,
            text = "Suspended (30 min)",
            progress = 0.4f
        )
    }
}
