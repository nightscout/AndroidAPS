package app.aaps.pump.carelevo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.TextRef
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
    alarmActionHandler: CarelevoAlarmActionHandler,
    private val rh: ResourceHelper
) : ViewModel() {

    val event = alarmActionHandler.uiRequests

    /** The text of a message, for the host, which shows it from a coroutine where `stringResource` cannot run. */
    fun text(ref: TextRef): String = rh.gs(ref)
}
