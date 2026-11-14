package app.aaps.ui.compose.overview

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.keys.interfaces.TextRef

// IDE-only previews for BolusProgressBanner, in their own *Previews.kt file so JaCoCo leaves them out
// (see ActiveSceneBannerPreviews for why).

private fun previewState(percent: Int, stalled: Boolean = false) = BolusProgressState(
    insulin = 4.0,
    isSMB = false,
    isPriming = false,
    percent = percent,
    status = TextRef.Literal("Delivering 1.80U"),
    wearStatus = TextRef.Literal("Delivering 1.80U"),
    delivered = PumpInsulin(1.8),
    stopPressed = false,
    stopDeliveryEnabled = true,
    stalled = stalled
)

@Preview(showBackground = true)
@Composable
internal fun BolusProgressBannerRunningPreview() {
    MaterialTheme {
        Surface {
            BolusProgressBannerContent(state = previewState(percent = 45), onStop = {}, onDismiss = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun BolusProgressBannerStalledPreview() {
    MaterialTheme {
        Surface {
            BolusProgressBannerContent(state = previewState(percent = 45, stalled = true), onStop = {}, onDismiss = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun BolusProgressBannerConnectingPreview() {
    MaterialTheme {
        Surface {
            BolusProgressBannerContent(
                state = previewState(percent = 0).copy(status = TextRef.Literal("")),
                onStop = {},
                onDismiss = {},
                pumpStatus = "Connecting for 12s"
            )
        }
    }
}
