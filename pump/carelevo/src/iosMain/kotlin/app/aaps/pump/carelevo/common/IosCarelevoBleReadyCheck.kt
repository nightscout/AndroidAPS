package app.aaps.pump.carelevo.common

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
import app.aaps.pump.carelevo.di.CarelevoCentral
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The iOS Bluetooth check before the pairing wizard scans.
 *
 * Android asks `BlePreCheck`, which also covers runtime permissions. iOS has no permission to check
 * up front - CoreBluetooth asks the user the first time the app uses the radio, and reports the
 * refusal afterwards as `UNAUTHORIZED` - so the adapter state carries every case on its own and
 * maps one to one onto the messages Android already shows:
 *
 * - `OFF` -> `ble_not_enabled`. The app cannot offer to switch Bluetooth on; iOS does not allow it,
 *   which is why `rememberBluetoothEnableRequest` returns null here. The user does it in Control
 *   Centre or Settings.
 * - `UNAUTHORIZED` -> `ble_permissions_missing`, whose text already says to grant it in the app
 *   settings, which is exactly where an iOS user goes.
 * - `UNSUPPORTED` -> `ble_not_supported`.
 * - `UNKNOWN` -> nothing yet. CoreBluetooth reports the real state a moment after the app starts, so
 *   showing a dialog on `UNKNOWN` would flash a false "Bluetooth is off" at every launch. Waiting is
 *   correct: the state always resolves.
 *
 * No new strings were needed, and the three reused ones are already translated.
 *
 * It reads the [CarelevoCentral] central rather than the phone's, so that the emulated patch, which
 * reports itself as on, is not blocked by a dialog about real hardware.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosCarelevoBleReadyCheck(
    @CarelevoCentral private val bleCentral: BleCentral
) : CarelevoBleReadyCheck {

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
