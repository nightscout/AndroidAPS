package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.resources.ResourceHelper

class DanaRS_Packet_Notify_Alarm(
    private val aapsLogger: AAPSLogger,
    private val resourceHelper: ResourceHelper
) : DanaRS_Packet() {

    init {
        type = BleCommandUtil.DANAR_PACKET__TYPE_NOTIFY
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_NOTIFY__ALARM
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
                errorString = resourceHelper.gs(R.string.lowbattery)
            0x05       ->  // Shutdown
                errorString = resourceHelper.gs(R.string.lowbattery)
            0x06       ->  // Basal Compare
                errorString = "BasalCompare ????"
            0x09       ->  // Empty Reservoir
                errorString = resourceHelper.gs(R.string.emptyreservoir)
            0x07, 0xFF ->  // Blood sugar measurement alert
                errorString = resourceHelper.gs(R.string.bloodsugarmeasurementalert)
            0x08, 0xFE ->  // Remaining insulin level
                errorString = resourceHelper.gs(R.string.remaininsulinalert)
            0xFD       ->  // Blood sugar check miss alarm
                errorString = "Blood sugar check miss alarm ???"
        }
        // No error no need to upload anything
        if (errorString == "") {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Error detected: $errorString")
            return
        }
        NSUpload.uploadError(errorString)
    }

    override fun getFriendlyName(): String {
        return "NOTIFY__ALARM"
    }
}