package app.aaps.core.ui.compose.pump

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Gate composable that ensures Bluetooth runtime permissions
 * ([Manifest.permission.BLUETOOTH_SCAN] and [Manifest.permission.BLUETOOTH_CONNECT])
 * are granted before rendering its [content].
 *
 * Flow:
 * 1. On first composition, checks whether both permissions are granted.
 * 2. If granted → renders [content].
 * 3. If missing → automatically launches the system permission request.
 * 4. After the user responds: either renders [content] or [deniedContent],
 *    passing a `requestAgain` lambda so the caller can offer an inline retry.
 *
 * No dialogs are shown — the caller fully controls the denied UI so it can be
 * inlined into a pairing wizard or similar flow.
 */
@Composable
fun BluetoothPermissionsHost(
    deniedContent: @Composable (requestAgain: () -> Unit) -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val permissions = remember {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }

    fun allGranted(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(allGranted()) }
    var requested by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it }
        requested = true
    }

    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(permissions)
        }
    }

    if (granted) {
        content()
    } else if (requested) {
        deniedContent { launcher.launch(permissions) }
    }
    // else: request in flight, render nothing
}
