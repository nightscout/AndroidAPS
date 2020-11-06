package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRS_Packet_General_Initial_Screen_Information(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__INITIAL_SCREEN_INFORMATION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 17) {
            failed = true
            return
        } else failed = false
        var dataIndex = DATA_START
        var dataSize = 1
        val status = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        danaPump.pumpSuspended = status and 0x01 == 0x01
        danaPump.isTempBasalInProgress = status and 0x10 == 0x10
        danaPump.isExtendedInProgress = status and 0x04 == 0x04
        danaPump.isDualBolusInProgress = status and 0x08 == 0x08
        dataIndex += dataSize
        dataSize = 2
        danaPump.dailyTotalUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        danaPump.maxDailyTotalUnits = (byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0).toInt()
        dataIndex += dataSize
        dataSize = 2
        danaPump.reservoirRemainingUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        danaPump.currentBasal = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 1
        danaPump.tempBasalPercent = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.batteryRemaining = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaPump.extendedBolusAbsoluteRate = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        danaPump.iob = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        if (data.size >= 18) {
            //protocol 10+
            dataIndex += dataSize
            dataSize = 1
            danaPump.errorState = DanaPump.ErrorState[byteArrayToInt(getBytes(data, dataIndex, dataSize))]
                ?: DanaPump.ErrorState.NONE
            aapsLogger.debug(LTag.PUMPCOMM, "ErrorState: " + danaPump.errorState.name)
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump suspended: " + danaPump.pumpSuspended)
        aapsLogger.debug(LTag.PUMPCOMM, "Temp basal in progress: " + danaPump.isTempBasalInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended in progress: " + danaPump.isExtendedInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Dual in progress: " + danaPump.isDualBolusInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Daily units: " + danaPump.dailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Max daily units: " + danaPump.maxDailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Reservoir remaining units: " + danaPump.reservoirRemainingUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Battery: " + danaPump.batteryRemaining)
        aapsLogger.debug(LTag.PUMPCOMM, "Current basal: " + danaPump.currentBasal)
        aapsLogger.debug(LTag.PUMPCOMM, "Temp basal percent: " + danaPump.tempBasalPercent)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended absolute rate: " + danaPump.extendedBolusAbsoluteRate)
    }

    override fun getFriendlyName(): String {
        return "REVIEW__INITIAL_SCREEN_INFORMATION"
    }
}