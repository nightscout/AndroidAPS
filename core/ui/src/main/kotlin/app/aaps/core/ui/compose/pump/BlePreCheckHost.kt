package app.aaps.core.ui.compose.pump

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BlePreCheckResult
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.dialogs.OkDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Composable that checks BLE readiness and shows appropriate dialogs on failure.
 *
 * Calls [BlePreCheck.checkBleReady] on first composition. On failure, shows an
 * [OkDialog] describing the issue. When dismissed, calls [onFailed].
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
    val context = LocalContext.current
    var checkResult by remember { mutableStateOf<BlePreCheckResult?>(null) }

    LaunchedEffect(Unit) {
        checkResult = withContext(Dispatchers.IO) {
            blePreCheck.checkBleReady(context)
        }
    }

    when (checkResult) {
        BlePreCheckResult.BLE_NOT_SUPPORTED -> {
            OkDialog(
                title = stringResource(R.string.message),
                message = stringResource(R.string.ble_not_supported),
                onDismiss = {
                    checkResult = null
                    onFailed?.invoke()
                }
            )
        }

        BlePreCheckResult.BLE_NOT_ENABLED -> {
            OkDialog(
                title = stringResource(R.string.message),
                message = stringResource(R.string.ble_not_enabled),
                onDismiss = {
                    checkResult = null
                    onFailed?.invoke()
                }
            )
        }

        BlePreCheckResult.PERMISSIONS_MISSING -> {
            OkDialog(
                title = stringResource(R.string.message),
                message = stringResource(R.string.ble_permissions_missing),
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
