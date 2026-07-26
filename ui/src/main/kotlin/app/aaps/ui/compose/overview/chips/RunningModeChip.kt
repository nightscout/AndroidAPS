package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.RM
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.ExcludeFromJacocoGeneratedReport
import app.aaps.core.ui.compose.icons.IcLoopClosed
import app.aaps.core.ui.compose.icons.IcLoopDisabled
import app.aaps.core.ui.compose.icons.IcLoopDisconnected
import app.aaps.core.ui.compose.icons.IcLoopLgs
import app.aaps.core.ui.compose.icons.IcLoopOpen
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.core.ui.compose.icons.IcLoopPausedDst
import app.aaps.core.ui.compose.icons.IcLoopPausedPump
import app.aaps.core.ui.compose.icons.IcLoopSuperbolus
import app.aaps.core.ui.compose.loopColor
import app.aaps.ui.compose.overview.graphs.TriangleShape

@Composable
fun RunningModeChip(
    mode: RM.Mode,
    text: String,
    progress: Float,
    modifier: Modifier = Modifier,
    remaining: String = "",
    sceneManaged: Boolean = false,
    smbEnabled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    val isTemporary = mode.mustBeTemporary()
    val iconColor = mode.toColor()
    val containerColor = if (isTemporary) iconColor.copy(alpha = 0.2f) else Color.Transparent
    val haptic = LocalHapticFeedback.current

    // Disable the clickable Surface's 48dp minimum interactive size so this content-width chip
    // isn't padded to 48dp and centered, which would inset it ~6dp from the column's left edge
    // (same guard IobChip uses).
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            enabled = enabled,
            shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
            color = containerColor,
            modifier = modifier
                .height(AapsSpacing.chipHeight)
        ) {
            Box(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .fillMaxHeight()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = AapsSpacing.medium, end = AapsSpacing.small, top = AapsSpacing.small, bottom = AapsSpacing.small)
                ) {
                    Box(modifier = Modifier.size(AapsSpacing.chipIconSize)) {
                        Icon(
                            imageVector = mode.toIcon(),
                            contentDescription = text,
                            tint = iconColor,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (smbEnabled) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 4.dp, y = 4.dp)
                                    .size(13.dp)
                                    .background(AapsTheme.elementColors.insulin, TriangleShape)
                            )
                        }
                    }
                    if (remaining.isNotEmpty()) {
                        Text(
                            text = remaining,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = AapsSpacing.small)
                        )
                    }
                    if (sceneManaged) {
                        SceneBadge(modifier = Modifier.padding(start = AapsSpacing.small))
                    }
                }
                // Progress bar overlaid at the bottom (out of flow) so the Row above stays vertically
                // centered — consistent with the other chips. Manual track/fill instead of
                // LinearProgressIndicator: the indicator's large (~240dp) intrinsic width would drive
                // this chip's IntrinsicSize.Max width to full width. Empty Boxes add no intrinsic width.
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(AapsSpacing.chipProgressHeight)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(iconColor.copy(alpha = 0.3f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(AapsSpacing.chipProgressHeight)
                                .background(iconColor)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Extension to get theme color for RM.Mode.
 * Thin composable wrapper around the shared [loopColor] mapping.
 */
@Composable
internal fun RM.Mode.toColor(): Color = loopColor(AapsTheme.generalColors)

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
    RM.Mode.SUSPENDED_BY_PUMP -> IcLoopPausedPump
    RM.Mode.SUSPENDED_BY_DST  -> IcLoopPausedDst
    RM.Mode.SUSPENDED_BY_USER -> IcLoopPaused

    RM.Mode.RESUME            -> IcLoopClosed
}

@ExcludeFromJacocoGeneratedReport
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

@ExcludeFromJacocoGeneratedReport
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

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun RunningModeChipClosedLoopSmbPreview() {
    MaterialTheme {
        RunningModeChip(
            mode = RM.Mode.CLOSED_LOOP,
            text = "Closed Loop",
            progress = 0f,
            smbEnabled = true
        )
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun RunningModeChipOpenLoopSmbPreview() {
    MaterialTheme {
        RunningModeChip(
            mode = RM.Mode.OPEN_LOOP,
            text = "Open Loop",
            progress = 0f,
            smbEnabled = true
        )
    }
}
