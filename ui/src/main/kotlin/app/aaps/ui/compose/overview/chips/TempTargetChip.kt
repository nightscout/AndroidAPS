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
import app.aaps.core.data.model.TT
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcTtActivity
import app.aaps.core.ui.compose.icons.IcTtEatingSoon
import app.aaps.core.ui.compose.icons.IcTtHypo
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.ui.compose.main.TempTargetChipState

@Composable
fun TempTargetChip(
    targetText: String,
    state: TempTargetChipState,
    progress: Float,
    reason: TT.Reason?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = when (state) {
        TempTargetChipState.Active   -> reason.toIconColor()
        TempTargetChipState.Adjusted -> AapsTheme.generalColors.adjusted
        TempTargetChipState.None     -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = when (state) {
        TempTargetChipState.Active   -> iconColor.copy(alpha = 0.2f)
        TempTargetChipState.Adjusted -> iconColor.copy(alpha = 0.2f)
        TempTargetChipState.None     -> Color.Transparent
    }
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
                    imageVector = reason.toIcon(),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                Text(
                    text = targetText,
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

@Composable
private fun TT.Reason?.toIconColor(): Color = when (this) {
    TT.Reason.EATING_SOON  -> AapsTheme.generalColors.ttEatingSoon
    TT.Reason.ACTIVITY     -> AapsTheme.generalColors.ttActivity
    TT.Reason.HYPOGLYCEMIA -> AapsTheme.generalColors.ttHypoglycemia
    else                   -> AapsTheme.generalColors.ttCustom // Custom, Automation, Wear, null
}

private fun TT.Reason?.toIcon(): ImageVector = when (this) {
    TT.Reason.EATING_SOON  -> IcTtEatingSoon
    TT.Reason.ACTIVITY     -> IcTtActivity
    TT.Reason.HYPOGLYCEMIA -> IcTtHypo
    else                   -> IcTtManual // Custom, Automation, Wear, null
}

@Preview(showBackground = true)
@Composable
private fun TempTargetChipActivePreview() {
    MaterialTheme {
        TempTargetChip(
            targetText = "5.5 - 5.5 (30 min)",
            state = TempTargetChipState.Active,
            progress = 0.5f,
            reason = TT.Reason.EATING_SOON,
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TempTargetChipNonePreview() {
    MaterialTheme {
        TempTargetChip(
            targetText = "5.0 - 7.0",
            state = TempTargetChipState.None,
            progress = 0f,
            reason = null,
            onClick = {}
        )
    }
}
