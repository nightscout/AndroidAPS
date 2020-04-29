package info.nightscout.androidaps.plugins.pump.danaRS.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaRS.encryption.BleEncryption
import javax.inject.Inject

class DanaRS_Packet_General_Initial_Screen_Information(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaRPump: DanaRPump

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
        danaRPump.pumpSuspended = status and 0x01 == 0x01
        danaRPump.isTempBasalInProgress = status and 0x10 == 0x10
        danaRPump.isExtendedInProgress = status and 0x04 == 0x04
        danaRPump.isDualBolusInProgress = status and 0x08 == 0x08
        dataIndex += dataSize
        dataSize = 2
        danaRPump.dailyTotalUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        danaRPump.maxDailyTotalUnits = (byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0).toInt()
        dataIndex += dataSize
        dataSize = 2
        danaRPump.reservoirRemainingUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        danaRPump.currentBasal = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 1
        danaRPump.tempBasalPercent = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaRPump.batteryRemaining = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.extendedBolusAbsoluteRate = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        danaRPump.iob = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        if (data.size >= 18) {
            //protocol 10+
            dataIndex += dataSize
            dataSize = 1
            danaRPump.errorState = DanaRPump.ErrorState[byteArrayToInt(getBytes(data, dataIndex, dataSize))]
                ?: DanaRPump.ErrorState.NONE
            aapsLogger.debug(LTag.PUMPCOMM, "ErrorState: " + danaRPump.errorState.name)
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump suspended: " + danaRPump.pumpSuspended)
        aapsLogger.debug(LTag.PUMPCOMM, "Temp basal in progress: " + danaRPump.isTempBasalInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended in progress: " + danaRPump.isExtendedInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Dual in progress: " + danaRPump.isDualBolusInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Daily units: " + danaRPump.dailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Max daily units: " + danaRPump.maxDailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Reservoir remaining units: " + danaRPump.reservoirRemainingUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Battery: " + danaRPump.batteryRemaining)
        aapsLogger.debug(LTag.PUMPCOMM, "Current basal: " + danaRPump.currentBasal)
        aapsLogger.debug(LTag.PUMPCOMM, "Temp basal percent: " + danaRPump.tempBasalPercent)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended absolute rate: " + danaRPump.extendedBolusAbsoluteRate)
    }

    override fun getFriendlyName(): String {
        return "REVIEW__INITIAL_SCREEN_INFORMATION"
    }
}