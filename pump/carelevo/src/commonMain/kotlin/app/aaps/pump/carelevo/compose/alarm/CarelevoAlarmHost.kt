package app.aaps.pump.carelevo.compose.alarm

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.pump.rememberBluetoothEnableRequest
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoAlarmViewModel

/**
 * Hosts the two UI requests [app.aaps.pump.carelevo.common.CarelevoAlarmActionHandler] cannot fulfill
 * itself: asking to turn Bluetooth on (or, where the platform cannot ask, saying it is off), and a failure
 * message. The alarms themselves are pump alerts ([app.aaps.core.interfaces.alerts.Alerts], raised by
 * `CarelevoAlarmNotifier.showAlerts`): the core shows the card and the alarm screen, both with the clear button —
 * this host renders nothing itself.
 */
@Composable
internal fun CarelevoAlarmHost(
    aapsLogger: AAPSLogger,
    snackbarHostState: SnackbarHostState
) {
    val viewModel: CarelevoAlarmViewModel = metroViewModel()

    val requestBluetoothEnable = rememberBluetoothEnableRequest { enabled -> aapsLogger.debug(LTag.PUMPCOMM, "bluetooth enable for alarm, turned on=$enabled") }

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
