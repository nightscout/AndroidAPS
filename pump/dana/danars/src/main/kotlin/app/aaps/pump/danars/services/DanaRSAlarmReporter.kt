package app.aaps.pump.danars.services

import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.runBlocking

/**
 * Shows a pump alarm to the user in the same way, whether the pump sent it as an alarm notify
 * or refused the connection with an error ("PUMP" reply to the pump check).
 */
@SingleIn(AppScope::class)
@Inject
class DanaRSAlarmReporter(
    private val rh: ResourceHelper,
    private val notificationManager: NotificationManager,
    private val pumpSync: PumpSync,
    private val danaPump: DanaPump
) {

    /** Posts the alarm notification and stores the alarm as an announcement */
    fun report(text: String) {
        notificationManager.post(NotificationId.DANA_PUMP_ALARM, text)
        runBlocking { pumpSync.insertAnnouncement(text, null, danaPump.pumpType(), danaPump.serialNumber) }
    }

    /**
     * Text for the "PUMP" reply to the pump check.
     * Dana-i2 adds 1 byte of error flags, older pumps send none ([errorFlags] is null).
     * If more than one flag is set, the first one in this list is used.
     */
    fun pumpCheckErrorText(errorFlags: Int?): String =
        when {
            errorFlags == null       -> rh.gs(R.string.pumperror)
            errorFlags and 0x01 != 0 -> rh.gs(R.string.lowbattery)
            errorFlags and 0x02 != 0 -> rh.gs(R.string.occlusion)
            errorFlags and 0x04 != 0 -> rh.gs(R.string.pumperror) // System Error
            errorFlags and 0x08 != 0 -> rh.gs(R.string.pumperror) // System Error (I2C)
            errorFlags and 0x10 != 0 -> rh.gs(R.string.danai2_pump_check_error)
            errorFlags and 0x20 != 0 -> rh.gs(R.string.pumpshutdown)
            errorFlags and 0x40 != 0 -> rh.gs(R.string.pumperror) // System Error (Internal Memory)
            else                     -> rh.gs(R.string.pumperror)
        }
}
