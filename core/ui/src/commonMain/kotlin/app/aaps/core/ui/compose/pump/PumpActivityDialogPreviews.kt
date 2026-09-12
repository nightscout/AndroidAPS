package app.aaps.core.ui.compose.pump

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.keys.interfaces.TextRef

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun PreviewBolusInProgress() {
    MaterialTheme {
        PumpActivityCard(
            bolusState = BolusProgressState(
                insulin = 4.0,
                isSMB = false,
                isPriming = false,
                percent = 45,
                status = TextRef.Literal("Delivering 1.80U"),
                wearStatus = TextRef.Literal("Delivering 1.80U"),
                delivered = PumpInsulin(1.8),
                stopPressed = false,
                stopDeliveryEnabled = true
            ),
            pumpStatus = "Connected",
            queueStatus = null,
            onStop = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun PreviewBolusStopPressed() {
    MaterialTheme {
        PumpActivityCard(
            bolusState = BolusProgressState(
                insulin = 4.0,
                isSMB = false,
                isPriming = false,
                percent = 45,
                status = TextRef.Literal("Delivering 1.80U"),
                wearStatus = TextRef.Literal("Delivering 1.80U"),
                delivered = PumpInsulin(1.8),
                stopPressed = true,
                stopDeliveryEnabled = true
            ),
            pumpStatus = "",
            queueStatus = null,
            onStop = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun PreviewBolusCompleted() {
    MaterialTheme {
        PumpActivityCard(
            bolusState = BolusProgressState(
                insulin = 4.0,
                isSMB = false,
                isPriming = false,
                percent = 100,
                status = TextRef.Literal("Bolus 4.00U delivered successfully"),
                wearStatus = TextRef.Literal("Bolus 4.00U delivered successfully"),
                delivered = PumpInsulin(4.0),
                stopPressed = false,
                stopDeliveryEnabled = true
            ),
            pumpStatus = "",
            queueStatus = null,
            onStop = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun PreviewBolusIndeterminate() {
    MaterialTheme {
        PumpActivityCard(
            bolusState = BolusProgressState(
                insulin = 2.5,
                isSMB = false,
                isPriming = false,
                percent = 0,
                status = TextRef.Literal(""),
                wearStatus = TextRef.Literal(""),
                delivered = PumpInsulin(0.0),
                stopPressed = false,
                stopDeliveryEnabled = false
            ),
            pumpStatus = "Connecting for 5s",
            queueStatus = AnnotatedString("BOLUS 2.50U"),
            onStop = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun PreviewBolusStalled() {
    MaterialTheme {
        PumpActivityCard(
            bolusState = BolusProgressState(
                insulin = 1.6,
                isSMB = false,
                isPriming = false,
                percent = 85,
                status = TextRef.Literal("Delivering 1.36U"),
                wearStatus = TextRef.Literal("Delivering 1.36U"),
                delivered = PumpInsulin(1.36),
                stopPressed = false,
                stopDeliveryEnabled = true,
                stalled = true
            ),
            pumpStatus = "",
            queueStatus = null,
            onStop = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
internal fun PreviewPumpStatusOnly() {
    MaterialTheme {
        PumpActivityCard(
            bolusState = null,
            pumpStatus = "Handshaking",
            queueStatus = AnnotatedString("READSTATUS"),
            onStop = {},
            onDismiss = {}
        )
    }
}
