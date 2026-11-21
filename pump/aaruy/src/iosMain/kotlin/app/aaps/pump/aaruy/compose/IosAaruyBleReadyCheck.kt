package app.aaps.pump.aaruy.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.stringResource
import app.aaps.pump.aaruy.di.AaruyCentral
import dev.zacsweers.metro.Inject

/**
 * The iOS Bluetooth check before the pairing wizard scans, as `IosCarelevoBleReadyCheck`. iOS has no permission to check
 * up front: CoreBluetooth asks the first time the app uses the radio, and reports a refusal as `UNAUTHORIZED`. So the
 * adapter state carries every case. `UNKNOWN` shows nothing: iOS reports the real state a moment after the start.
 *
 * It reads the [AaruyCentral] central, so the emulated pump, which reports itself as on, is not blocked by a dialog about
 * real hardware.
 */
@Inject
class IosAaruyBleReadyCheck(@AaruyCentral private val bleCentral: BleCentral) : AaruyBleReadyCheck {

    @Composable
    override fun Check(onReady: () -> Unit, onFailed: () -> Unit) {
        val adapterState by bleCentral.adapterState.collectAsState()
        when (adapterState) {
            AdapterState.ON           -> LaunchedEffect(Unit) { onReady() }
            AdapterState.OFF          -> Message(CoreUiStrings.ble_not_enabled, onFailed)
            AdapterState.UNAUTHORIZED -> Message(CoreUiStrings.ble_permissions_missing, onFailed)
            AdapterState.UNSUPPORTED  -> Message(CoreUiStrings.ble_not_supported, onFailed)
            AdapterState.UNKNOWN      -> Unit
        }
    }

    @Composable
    private fun Message(text: TextRef, onFailed: () -> Unit) {
        OkDialog(
            title = stringResource(CoreUiStrings.message),
            message = stringResource(text),
            onDismiss = onFailed
        )
    }
}
