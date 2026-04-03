package app.aaps.core.ui.compose.pump

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing

/**
 * Shared pump activity dialog showing pump status, queue info, and bolus progress.
 *
 * Two display modes:
 * - Modal (standard bolus): auto-opened, full scrim, not dismissable
 * - Non-modal (SMB / other): opened via FAB tap, dismissable
 */
@Composable
fun PumpActivityDialog(
    bolusState: BolusProgressState?,
    pumpStatus: String,
    queueStatus: String?,
    isModal: Boolean,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isModal) {
        // Modal: full-screen scrim + centered card, not dismissable
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { } // consume touches
                ),
            contentAlignment = Alignment.Center
        ) {
            PumpActivityCard(
                bolusState = bolusState,
                pumpStatus = pumpStatus,
                queueStatus = queueStatus,
                onStop = onStop
            )
        }
    } else {
        // Non-modal: standard dialog, dismissable
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            PumpActivityCard(
                bolusState = bolusState,
                pumpStatus = pumpStatus,
                queueStatus = queueStatus,
                onStop = onStop
            )
        }
    }
}

@Composable
private fun PumpActivityCard(
    bolusState: BolusProgressState?,
    pumpStatus: String,
    queueStatus: String?,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.xxLarge),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(AapsSpacing.xxLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bolus progress section
            if (bolusState != null) {
                BolusProgressSection(
                    state = bolusState,
                    onStop = onStop
                )
            }

            // Hide pump/queue status once delivery progress starts (percent > 0)
            // — the progress section already shows all needed info
            val hideStatus = bolusState != null && bolusState.percent > 0

            // Pump status section
            if (!hideStatus && pumpStatus.isNotEmpty()) {
                if (bolusState != null) Spacer(modifier = Modifier.height(AapsSpacing.extraLarge))
                Text(
                    text = pumpStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Queue status section
            if (!hideStatus && queueStatus != null) {
                Spacer(modifier = Modifier.height(AapsSpacing.medium))
                Text(
                    text = queueStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BolusProgressSection(
    state: BolusProgressState,
    onStop: () -> Unit
) {
    // Title
    Text(
        text = stringResource(R.string.goingtodeliver, state.insulin),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(AapsSpacing.large))

    // Status text
    if (state.status.isNotEmpty()) {
        Text(
            text = state.status,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(AapsSpacing.large))
    }

    // Progress bar
    if (state.percent > 0) {
        LinearProgressIndicator(
            progress = { state.percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(AapsSpacing.medium),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    } else {
        // Indeterminate when no progress received yet
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(AapsSpacing.medium),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    Spacer(modifier = Modifier.height(AapsSpacing.extraLarge))

    // Stop button
    if (state.percent < 100) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onStop,
                enabled = !state.stopPressed && state.stopDeliveryEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(
                    text = if (state.stopPressed) stringResource(R.string.stop_pressed)
                    else stringResource(R.string.stop)
                )
            }
        }
    }
}

// --- Previews ---

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewBolusInProgress() {
    MaterialTheme {
        PumpActivityCard(
            bolusState = BolusProgressState(
                insulin = 4.0,
                isSMB = false,
                isPriming = false,
                percent = 45,
                status = "Delivering 1.80U",
                delivered = 1.8,
                stopPressed = false,
                stopDeliveryEnabled = true
            ),
            pumpStatus = "Connected",
            queueStatus = null,
            onStop = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewBolusStopPressed() {
    MaterialTheme {
        PumpActivityCard(
            bolusState = BolusProgressState(
                insulin = 4.0,
                isSMB = false,
                isPriming = false,
                percent = 45,
                status = "Delivering 1.80U",
                delivered = 1.8,
                stopPressed = true,
                stopDeliveryEnabled = true
            ),
            pumpStatus = "",
            queueStatus = null,
            onStop = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewBolusCompleted() {
    MaterialTheme {
        PumpActivityCard(
            bolusState = BolusProgressState(
                insulin = 4.0,
                isSMB = false,
                isPriming = false,
                percent = 100,
                status = "Bolus 4.00U delivered successfully",
                delivered = 4.0,
                stopPressed = false,
                stopDeliveryEnabled = true
            ),
            pumpStatus = "",
            queueStatus = null,
            onStop = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewBolusIndeterminate() {
    MaterialTheme {
        PumpActivityCard(
            bolusState = BolusProgressState(
                insulin = 2.5,
                isSMB = false,
                isPriming = false,
                percent = 0,
                status = "",
                delivered = 0.0,
                stopPressed = false,
                stopDeliveryEnabled = false
            ),
            pumpStatus = "Connecting for 5s",
            queueStatus = "BOLUS 2.50U",
            onStop = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewPumpStatusOnly() {
    MaterialTheme {
        PumpActivityCard(
            bolusState = null,
            pumpStatus = "Handshaking",
            queueStatus = "READSTATUS",
            onStop = {}
        )
    }
}

