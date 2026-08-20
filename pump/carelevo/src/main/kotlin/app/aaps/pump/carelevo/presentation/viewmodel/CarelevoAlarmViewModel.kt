package app.aaps.pump.carelevo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import app.aaps.pump.carelevo.common.CarelevoAlarmActionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * UI shell over [CarelevoAlarmActionHandler] for [app.aaps.pump.carelevo.compose.alarm.CarelevoAlarmHost]:
 * re-exposes the handler's UI-request stream (Bluetooth-enable intent, failure toast) so the host has
 * one Hilt-scoped ViewModel to collect from. The actual alarm state machine — including clearing an
 * alarm — lives entirely in [CarelevoAlarmActionHandler], driven directly from
 * [app.aaps.pump.carelevo.common.CarelevoAlarmNotifier.showTopNotification]'s action button; this shell
 * does not sit in that path.
 */
@HiltViewModel
class CarelevoAlarmViewModel @Inject constructor(
    alarmActionHandler: CarelevoAlarmActionHandler
) : ViewModel() {

    val event = alarmActionHandler.uiRequests
}
