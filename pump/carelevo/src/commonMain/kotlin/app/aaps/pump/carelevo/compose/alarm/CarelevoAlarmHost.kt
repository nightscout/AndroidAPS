package app.aaps.pump.carelevo.compose.alarm

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoAlarmViewModel

/**
 * Hosts the two UI requests [app.aaps.pump.carelevo.common.CarelevoAlarmActionHandler] cannot fulfill
 * itself: asking to turn Bluetooth on (or, where the platform cannot ask, saying it is off), and a failure
 * message. Critical alarms surface through
 * the shared AAPS full-screen alarm ([app.aaps.core.interfaces.ui.UiInteraction.runAlarm], fired from
 * `CarelevoPumpPlugin.handleAlarms`) and every alarm's actual clear action lives on the persistent
 * notification card (`CarelevoAlarmNotifier.showTopNotification`) — this host renders nothing itself.
 */
@Composable
internal fun CarelevoAlarmHost(
    aapsLogger: AAPSLogger,
    snackbarHostState: SnackbarHostState
) {
    val viewModel: CarelevoAlarmViewModel = metroViewModel()

    val requestBluetoothEnable = rememberBluetoothEnableRequest(onEnabled = { aapsLogger.debug(LTag.PUMPCOMM, "bluetooth enabled for alarm") })

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                AlarmEvent.RequestBluetoothEnable -> {
                    if (requestBluetoothEnable != null) requestBluetoothEnable()
                    else snackbarHostState.showSnackbar(message = viewModel.text(CarelevoStrings.carelevo_toast_msg_bluetooth_not_enabled))
                }

                is AlarmEvent.ShowToastMessage    -> {
                    snackbarHostState.showSnackbar(
                        message = viewModel.text(event.message)
                    )
                }

                else                              -> Unit
            }
        }
    }
}
