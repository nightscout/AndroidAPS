package app.aaps.core.ui.compose.pump

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/** The system dialog that turns Bluetooth on. Since Android 13 an app cannot turn it on itself, only ask. */
@Composable
actual fun rememberBluetoothEnableRequest(onResult: (enabled: Boolean) -> Unit): (() -> Unit)? {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onResult(result.resultCode == Activity.RESULT_OK)
    }
    return { launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
}
