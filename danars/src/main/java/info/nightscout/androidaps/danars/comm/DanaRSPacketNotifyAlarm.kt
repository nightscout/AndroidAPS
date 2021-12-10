package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.R
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.resources.ResourceHelper
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
                errorString = rh.gs(R.string.batterydischarged)
            0x02       ->  // Pump Error
                errorString = rh.gs(R.string.pumperror) + " " + alarmCode
            0x03       ->  // Occlusion
                errorString = rh.gs(R.string.occlusion)
            0x04       ->  // LOW BATTERY
                errorString = rh.gs(R.string.pumpshutdown)
            0x05       ->  // Shutdown
                errorString = rh.gs(R.string.lowbattery)
            0x06       ->  // Basal Compare
                errorString = rh.gs(R.string.basalcompare)
            0x07, 0xFF ->  // Blood sugar measurement alert
                errorString = rh.gs(R.string.bloodsugarmeasurementalert)
            0x08, 0xFE ->  // Remaining insulin level
                errorString = rh.gs(R.string.remaininsulinalert)
            0x09       ->  // Empty Reservoir
                errorString = rh.gs(R.string.emptyreservoir)
            0x0A       ->  // Check shaft
                errorString = rh.gs(R.string.checkshaft)
            0x0B       ->  // Basal MAX
                errorString = rh.gs(R.string.basalmax)
            0x0C       ->  // Daily MAX
                errorString = rh.gs(R.string.dailymax)
            0xFD       ->  // Blood sugar check miss alarm
                errorString = rh.gs(R.string.missedbolus)
        }
        // No error no need to upload anything
        if (errorString == "") {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Error detected: $errorString")
            return
        }
        val notification = Notification(Notification.USER_MESSAGE, errorString, Notification.URGENT)
        rxBus.send(EventNewNotification(notification))
        pumpSync.insertAnnouncement(errorString, null, danaPump.pumpType(), danaPump.serialNumber)
    }

    override val friendlyName: String = "NOTIFY__ALARM"
}