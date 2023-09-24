package info.nightscout.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.pump.dana.DanaPump
import javax.inject.Inject

class DanaRSPacketNotifyAlarm(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var danaPump: DanaPump

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
                errorString = rh.gs(info.nightscout.pump.dana.R.string.batterydischarged)

            0x02       ->  // Pump Error
                errorString = rh.gs(info.nightscout.pump.dana.R.string.pumperror) + " " + alarmCode

            0x03       ->  // Occlusion
                errorString = rh.gs(info.nightscout.pump.dana.R.string.occlusion)

            0x04       ->  // LOW BATTERY
                errorString = rh.gs(info.nightscout.pump.dana.R.string.pumpshutdown)

            0x05       ->  // Shutdown
                errorString = rh.gs(info.nightscout.pump.dana.R.string.lowbattery)

            0x06       ->  // Basal Compare
                errorString = rh.gs(info.nightscout.pump.dana.R.string.basalcompare)

            0x07, 0xFF ->  // Blood sugar measurement alert
                errorString = rh.gs(info.nightscout.pump.dana.R.string.bloodsugarmeasurementalert)

            0x08, 0xFE ->  // Remaining insulin level
                errorString = rh.gs(info.nightscout.pump.dana.R.string.remaininsulinalert)

            0x09       ->  // Empty Reservoir
                errorString = rh.gs(info.nightscout.pump.dana.R.string.emptyreservoir)

            0x0A       ->  // Check shaft
                errorString = rh.gs(info.nightscout.pump.dana.R.string.checkshaft)

            0x0B       ->  // Basal MAX
                errorString = rh.gs(info.nightscout.pump.dana.R.string.basalmax)

            0x0C       ->  // Daily MAX
                errorString = rh.gs(info.nightscout.pump.dana.R.string.dailymax)

            0xFD       ->  // Blood sugar check miss alarm
                errorString = rh.gs(info.nightscout.pump.dana.R.string.missedbolus)
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