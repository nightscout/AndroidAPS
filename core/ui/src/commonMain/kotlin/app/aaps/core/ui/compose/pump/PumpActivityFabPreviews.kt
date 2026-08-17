package app.aaps.core.ui.compose.pump

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.keys.interfaces.TextRef
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun PreviewPumpFabIcon() {
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
internal fun PreviewPumpFabSmbPercent() {
    MaterialTheme {
        PumpActivityFab(
            visible = true,
            bolusState = BolusProgressState(
                insulin = 0.3,
                isSMB = true,
                isPriming = false,
                percent = 42,
                status = TextRef.Literal("Delivering 0.13U"),
                wearStatus = TextRef.Literal("Delivering 0.13U"),
                delivered = PumpInsulin(0.13),
                stopPressed = false,
                stopDeliveryEnabled = true
            ),
            onClick = {}
        )
    }
}
