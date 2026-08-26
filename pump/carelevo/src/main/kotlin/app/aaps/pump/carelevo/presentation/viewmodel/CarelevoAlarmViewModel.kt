package app.aaps.pump.carelevo.presentation.viewmodel

import android.os.Handler
import android.os.HandlerThread
import androidx.lifecycle.ViewModel
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.common.CarelevoAlarmActionHandler
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.ext.transformNotificationStringResources
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * UI shell over [CarelevoAlarmActionHandler] for the in-app full-screen alarm: owns ONLY the sound
 * lifecycle (start/stop/mute/mute-5-min) and forwards clear requests to the handler's shared
 * state machine. The alarm queue, its empty event, and UI requests (Bluetooth-enable intent,
 * failure toast) are the handler's — exposed here unchanged so the Compose host has one ViewModel
 * to talk to.
 */
@HiltViewModel
class CarelevoAlarmViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val uiInteraction: UiInteraction,
    private val rh: ResourceHelper,
    private val alarmActionHandler: CarelevoAlarmActionHandler
) : ViewModel() {

    val alarmQueue = alarmActionHandler.alarmQueue
    val alarmQueueEmptyEvent = alarmActionHandler.alarmQueueEmptyEvent
    val event = alarmActionHandler.uiRequests

    /** The alarm the host is currently presenting; used for the alarm-dialog status text. */
    var alarmInfo: CarelevoAlarmInfo? = null

    private val sound = CoreUiR.raw.error
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private fun startAlarm(reason: String) {
        if (sound == 0) return
        val title = rh.gs(R.string.carelevo)
        val status = alarmInfo?.let { rh.gs(it.cause.transformNotificationStringResources().first) } ?: title
        aapsLogger.debug(LTag.PUMPCOMM, "startAlarm reason=$reason status=$status")
        uiInteraction.runAlarm(status, title, sound)
    }

    private fun stopAlarm(reason: String) {
        uiInteraction.stopAlarm(reason)
    }

    fun triggerEvent(event: AlarmEvent) {
        when (event) {
            is AlarmEvent.ClearAlarm -> {
                stopAlarm("Confirm Click")
                alarmInfo = event.info
                alarmActionHandler.triggerEvent(event)
            }

            is AlarmEvent.Mute       -> stopAlarm("Mute Click")

            is AlarmEvent.Mute5min   -> {
                stopAlarm("Mute5min Click")
                handler.postDelayed({ startAlarm("post") }, T.mins(5).msecs())
            }

            is AlarmEvent.StartAlarm -> startAlarm("start")
            else                     -> Unit
        }
    }

    fun loadActiveAlarms() = alarmActionHandler.loadActiveAlarms()

    override fun onCleared() {
        super.onCleared()
        // Drop any pending Mute5min re-arm and stop the dedicated thread — without this the
        // HandlerThread leaks for the process lifetime and a delayed startAlarm could fire
        // against a cleared ViewModel.
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }
}
