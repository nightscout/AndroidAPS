package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class DanaRS_Packet_Notify_Alarm(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var nsUpload: NSUpload

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
                errorString = resourceHelper.gs(R.string.batterydischarged)
            0x02       ->  // Pump Error
                errorString = resourceHelper.gs(R.string.pumperror) + " " + alarmCode
            0x03       ->  // Occlusion
                errorString = resourceHelper.gs(R.string.occlusion)
            0x04       ->  // LOW BATTERY
                errorString = resourceHelper.gs(R.string.pumpshutdown)
            0x05       ->  // Shutdown
                errorString = resourceHelper.gs(R.string.lowbattery)
            0x06       ->  // Basal Compare
                errorString = resourceHelper.gs(R.string.basalcompare)
            0x07, 0xFF ->  // Blood sugar measurement alert
                errorString = resourceHelper.gs(R.string.bloodsugarmeasurementalert)
            0x08, 0xFE ->  // Remaining insulin level
                errorString = resourceHelper.gs(R.string.remaininsulinalert)
            0x09       ->  // Empty Reservoir
                errorString = resourceHelper.gs(R.string.emptyreservoir)
            0x0A       ->  // Check shaft
                errorString = resourceHelper.gs(R.string.checkshaft)
            0x0B       ->  // Basal MAX
                errorString = resourceHelper.gs(R.string.basalmax)
            0x0C       ->  // Daily MAX
                errorString = resourceHelper.gs(R.string.dailymax)
            0xFD       ->  // Blood sugar check miss alarm
                errorString = resourceHelper.gs(R.string.missedbolus)
        }
        // No error no need to upload anything
        if (errorString == "") {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Error detected: $errorString")
            return
        }
        val notification = Notification(Notification.USERMESSAGE, errorString, Notification.URGENT)
        rxBus.send(EventNewNotification(notification))
        nsUpload.uploadError(errorString)
    }

    override fun getFriendlyName(): String {
        return "NOTIFY__ALARM"
    }
}