package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketGeneralInitialScreenInformation @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    var isTempBasalInProgress = false
    var isExtendedInProgress = false
    var isDualBolusInProgress = false

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__INITIAL_SCREEN_INFORMATION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 17) {
            failed = true
            return
        } else failed = false
        val status = intFromBuff(data, 0, 1)
        danaPump.pumpSuspended = status and 0x01 == 0x01
        isTempBasalInProgress = status and 0x10 == 0x10
        isExtendedInProgress = status and 0x04 == 0x04
        isDualBolusInProgress = status and 0x08 == 0x08
        danaPump.dailyTotalUnits = intFromBuff(data, 1, 2) / 100.0
        danaPump.maxDailyTotalUnits = intFromBuff(data, 3, 2) / 100
        danaPump.reservoirRemainingUnits = intFromBuff(data, 5, 2) / 100.0
        danaPump.currentBasal = intFromBuff(data, 7, 2) / 100.0
        val tempBasalPercent = intFromBuff(data, 9, 1)
        danaPump.batteryRemaining = intFromBuff(data, 10, 1)
        val extendedBolusAbsoluteRate = intFromBuff(data, 11, 2) / 100.0
        danaPump.iob = intFromBuff(data, 13, 2) / 100.0
        if (data.size >= 18) {
            //protocol 10+
            danaPump.errorState = DanaPump.ErrorState[intFromBuff(data, 15, 1)]
                ?: DanaPump.ErrorState.NONE
            aapsLogger.debug(LTag.PUMPCOMM, "ErrorState: " + danaPump.errorState.name)
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump suspended: " + danaPump.pumpSuspended)
        aapsLogger.debug(LTag.PUMPCOMM, "Temp basal in progress: $isTempBasalInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended in progress: $isExtendedInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "Dual in progress: $isDualBolusInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "Daily units: " + danaPump.dailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Max daily units: " + danaPump.maxDailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Reservoir remaining units: " + danaPump.reservoirRemainingUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Battery: " + danaPump.batteryRemaining)
        aapsLogger.debug(LTag.PUMPCOMM, "Current basal: " + danaPump.currentBasal)
        aapsLogger.debug(LTag.PUMPCOMM, "Temp basal percent: $tempBasalPercent")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended absolute rate: $extendedBolusAbsoluteRate")
    }

    override val friendlyName: String = "REVIEW__INITIAL_SCREEN_INFORMATION"
}