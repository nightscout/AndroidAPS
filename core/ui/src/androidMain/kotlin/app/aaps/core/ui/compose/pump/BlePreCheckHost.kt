package app.aaps.core.ui.compose.pump

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BlePreCheckResult
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Composable that checks BLE readiness and shows appropriate dialogs on failure.
 *
 * Calls [BlePreCheck.checkBleReady] on first composition. When Bluetooth is off, it first asks the system to turn it
 * on ([rememberBluetoothEnableRequest]) and checks again when the user said yes. On any other failure, or when the
 * user said no, it shows an [OkDialog] describing the issue. When dismissed, calls [onFailed].
 *
 * Permissions are NOT requested here — they should already be granted via
 * [PermissionsViewModel] at app startup. The PERMISSIONS_MISSING case is a
 * fallback dialog directing the user to settings.
 *
 * @param blePreCheck The BLE pre-check instance
 * @param onReady Called once when BLE is confirmed ready
 * @param onFailed Called when BLE is not ready and the dialog is dismissed
 */
@Composable
fun BlePreCheckHost(
    blePreCheck: BlePreCheck,
    onReady: (() -> Unit)? = null,
    onFailed: (() -> Unit)? = null
) {
    var checkResult by remember { mutableStateOf<BlePreCheckResult?>(null) }
    // Bumped to run the check again, after the user turned Bluetooth on.
    var checks by remember { mutableIntStateOf(0) }
    var enableAsk by remember { mutableStateOf(EnableAsk.NOT_ASKED) }
    val requestBluetooth = rememberBluetoothEnableRequest { enabled ->
        enableAsk = EnableAsk.ANSWERED
        if (enabled) {
            checkResult = null
            checks++
        }
    }

    LaunchedEffect(checks) {
        checkResult = withContext(Dispatchers.IO) {
            blePreCheck.checkBleReady()
        }
    }

    when (checkResult) {
        BlePreCheckResult.BLE_NOT_SUPPORTED -> {
            OkDialog(
                title = stringResource(CoreUiStrings.message),
                message = stringResource(CoreUiStrings.ble_not_supported),
                onDismiss = {
                    checkResult = null
                    onFailed?.invoke()
                }
            )
        }

        BlePreCheckResult.BLE_NOT_ENABLED -> when {
            // Ask once; the system shows its own dialog.
            requestBluetooth != null && enableAsk == EnableAsk.NOT_ASKED -> LaunchedEffect(Unit) {
                enableAsk = EnableAsk.WAITING
                requestBluetooth()
            }

            enableAsk == EnableAsk.WAITING                               -> Unit

            // The user said no, it is still off, or this platform cannot ask.
            else                                                         -> OkDialog(
                title = stringResource(CoreUiStrings.message),
                message = stringResource(CoreUiStrings.ble_not_enabled),
                onDismiss = {
                    checkResult = null
                    onFailed?.invoke()
                }
            )
        }

        BlePreCheckResult.PERMISSIONS_MISSING -> {
            OkDialog(
                title = stringResource(CoreUiStrings.message),
                message = stringResource(CoreUiStrings.ble_permissions_missing),
                onDismiss = {
                    checkResult = null
                    onFailed?.invoke()
                }
            )
        }

        BlePreCheckResult.READY -> {
            LaunchedEffect(Unit) { onReady?.invoke() }
        }

        null -> {
            // Check hasn't completed yet — nothing to show
        }
    }
}

/** Where the request to turn Bluetooth on stands: asked at most once per check of this host. */
private enum class EnableAsk { NOT_ASKED, WAITING, ANSWERED }
