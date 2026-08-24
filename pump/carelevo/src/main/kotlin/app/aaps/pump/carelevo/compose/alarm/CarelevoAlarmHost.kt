package app.aaps.pump.carelevo.compose.alarm

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoAlarmViewModel

/**
 * Hosts the two UI requests [app.aaps.pump.carelevo.common.CarelevoAlarmActionHandler] cannot fulfill
 * itself: launching the Bluetooth-enable intent, and a failure toast. Critical alarms surface through
 * the shared AAPS full-screen alarm ([app.aaps.core.interfaces.ui.UiInteraction.runAlarm], fired from
 * `CarelevoPumpPlugin.handleAlarms`) and every alarm's actual clear action lives on the persistent
 * notification card (`CarelevoAlarmNotifier.showTopNotification`) — this host renders nothing itself.
 */
@Composable
internal fun CarelevoAlarmHost(
    aapsLogger: AAPSLogger,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val viewModel: CarelevoAlarmViewModel = hiltViewModel()

    val requestBtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            aapsLogger.debug(LTag.PUMPCOMM, "bluetooth enabled for alarm")
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                AlarmEvent.RequestBluetoothEnable -> {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    requestBtLauncher.launch(enableBtIntent)
                }

                is AlarmEvent.ShowToastMessage    -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(event.messageRes)
                    )
                }

                else                              -> Unit
            }
        }
    }
}
