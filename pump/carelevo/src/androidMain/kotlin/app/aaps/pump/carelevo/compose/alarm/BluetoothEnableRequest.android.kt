package app.aaps.pump.carelevo.compose.alarm

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/** The system dialog that turns Bluetooth on. */
@Composable
internal actual fun rememberBluetoothEnableRequest(onEnabled: () -> Unit): (() -> Unit)? {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onEnabled()
    }
    return { launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
}
