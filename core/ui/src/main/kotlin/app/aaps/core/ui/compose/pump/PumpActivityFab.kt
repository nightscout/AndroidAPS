package app.aaps.core.ui.compose.pump

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.compose.AapsSmallFab

/**
 * FAB indicating active pump communication.
 *
 * Visible when pump is communicating or bolus is in progress.
 * Shows pump icon normally, or delivery percentage during SMB.
 * Tap opens the PumpActivityDialog.
 */
@Composable
fun PumpActivityFab(
    visible: Boolean,
    bolusState: BolusProgressState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        AapsSmallFab(onClick = onClick) {
            if (bolusState != null && bolusState.isSMB && bolusState.percent in 1..99) {
                Text(
                    text = "${bolusState.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Vaccines,
                    contentDescription = null
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewPumpFabIcon() {
    MaterialTheme {
        PumpActivityFab(
            visible = true,
            bolusState = null,
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewPumpFabSmbPercent() {
    MaterialTheme {
        PumpActivityFab(
            visible = true,
            bolusState = BolusProgressState(
                insulin = 0.3,
                isSMB = true,
                isPriming = false,
                percent = 42,
                status = "Delivering 0.13U",
                delivered = 0.13,
                stopPressed = false,
                stopDeliveryEnabled = true
            ),
            onClick = {}
        )
    }
}
