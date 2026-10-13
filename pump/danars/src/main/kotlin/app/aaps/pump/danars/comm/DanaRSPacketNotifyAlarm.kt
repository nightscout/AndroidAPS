package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketNotifyAlarm @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val pumpSync: PumpSync,
    private val danaPump: DanaPump,
    private val uiInteraction: UiInteraction
) : DanaRSPacket() {

    init {
        type = BleEncryption.DANAR_PACKET__TYPE_NOTIFY
        opCode = BleEncryption.DANAR_PACKET__OPCODE_NOTIFY__ALARM
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        val alarmCode = byteArrayToInt(getBytes(data, DATA_START, 1))
        var errorString = ""
        when (alarmCode) {
            0x01       ->  // Battery 0% Alarm
                errorString = rh.gs(app.aaps.pump.dana.R.string.batterydischarged)

            0x02       ->  // Pump Error
                errorString = rh.gs(app.aaps.pump.dana.R.string.pumperror) + " " + alarmCode

            0x03       ->  // Occlusion
                errorString = rh.gs(app.aaps.pump.dana.R.string.occlusion)

            0x04       ->  // LOW BATTERY
                errorString = rh.gs(app.aaps.pump.dana.R.string.pumpshutdown)

            0x05       ->  // Shutdown
                errorString = rh.gs(app.aaps.pump.dana.R.string.lowbattery)

            0x06       ->  // Basal Compare
                errorString = rh.gs(app.aaps.pump.dana.R.string.basalcompare)

            0x07, 0xFF ->  // Blood sugar measurement alert
                errorString = rh.gs(app.aaps.pump.dana.R.string.bloodsugarmeasurementalert)

            0x08, 0xFE ->  // Remaining insulin level
                errorString = rh.gs(app.aaps.pump.dana.R.string.remaininsulinalert)

            0x09       ->  // Empty Reservoir
                errorString = rh.gs(app.aaps.pump.dana.R.string.emptyreservoir)

            0x0A       ->  // Check shaft
                errorString = rh.gs(app.aaps.pump.dana.R.string.checkshaft)

            0x0B       ->  // Basal MAX
                errorString = rh.gs(app.aaps.pump.dana.R.string.basalmax)

            0x0C       ->  // Daily MAX
                errorString = rh.gs(app.aaps.pump.dana.R.string.dailymax)

            0xFD       ->  // Blood sugar check miss alarm
                errorString = rh.gs(app.aaps.pump.dana.R.string.missedbolus)
        }
        // No error no need to upload anything
        if (errorString == "") {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Error detected: $errorString")
            return
        }
        uiInteraction.addNotification(Notification.USER_MESSAGE, errorString, Notification.URGENT)
        pumpSync.insertAnnouncement(errorString, null, danaPump.pumpType(), danaPump.serialNumber)
    }

    override val friendlyName: String = "NOTIFY__ALARM"
}