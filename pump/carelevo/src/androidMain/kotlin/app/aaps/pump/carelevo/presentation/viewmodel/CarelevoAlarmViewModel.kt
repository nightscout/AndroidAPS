package app.aaps.pump.carelevo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import app.aaps.pump.carelevo.common.CarelevoAlarmActionHandler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey

/**
 * UI shell over [CarelevoAlarmActionHandler] for [app.aaps.pump.carelevo.compose.alarm.CarelevoAlarmHost]:
 * re-exposes the handler's UI-request stream (Bluetooth-enable intent, failure toast) so the host has
 * one ViewModel to collect from. The actual alarm state machine — including clearing an alarm — lives
 * entirely in [CarelevoAlarmActionHandler], driven directly from
 * [app.aaps.pump.carelevo.common.CarelevoAlarmNotifier.showTopNotification]'s action button; this shell
 * does not sit in that path.
 */
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class CarelevoAlarmViewModel @Inject constructor(
    alarmActionHandler: CarelevoAlarmActionHandler
) : ViewModel() {

    val event = alarmActionHandler.uiRequests
}
