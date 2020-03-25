package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class MsgSetUserOptions(
    private val aapsLogger: AAPSLogger,
    danaRPump: DanaRPump
) : MessageBase() {

    init {
        SetCommand(0x330B)
        if (danaRPump.userOptionsFrompump == null) {
            // No options set -> Exiting
            aapsLogger.debug(LTag.PUMPCOMM, "NO USER OPTIONS LOADED EXITING!")
        } else {
            danaRPump.userOptionsFrompump!![0] = (if (danaRPump.timeDisplayType == 1) 0 else 1).toByte()
            danaRPump.userOptionsFrompump!![1] = danaRPump.buttonScrollOnOff.toByte()
            danaRPump.userOptionsFrompump!![2] = danaRPump.beepAndAlarm.toByte()
            danaRPump.userOptionsFrompump!![3] = danaRPump.lcdOnTimeSec.toByte()
            danaRPump.userOptionsFrompump!![4] = danaRPump.backlightOnTimeSec.toByte()
            danaRPump.userOptionsFrompump!![5] = danaRPump.selectedLanguage.toByte()
            danaRPump.userOptionsFrompump!![8] = danaRPump.units.toByte()
            danaRPump.userOptionsFrompump!![9] = danaRPump.shutdownHour.toByte()
            danaRPump.userOptionsFrompump!![27] = danaRPump.lowReservoirRate.toByte()
            for (element in danaRPump.userOptionsFrompump!!) {
                AddParamByte(element)
            }
            aapsLogger.debug(LTag.PUMPCOMM, "New message")
        }
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Setting user options: $result FAILED!!!")
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Setting user options: $result")
        }
    }
}