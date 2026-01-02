package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketOptionSetUserOption @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_OPTION__SET_USER_OPTION
        aapsLogger.debug(LTag.PUMPCOMM, "Setting user settings")
    }

    override fun getRequestParams(): ByteArray {
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "UserOptions:" + (System.currentTimeMillis() - danaPump.lastConnection) / 1000 + " s ago"
                + "\ntimeDisplayType24:" + danaPump.timeDisplayType24
                + "\nbuttonScroll:" + danaPump.buttonScrollOnOff
                + "\nbeepAndAlarm:" + danaPump.beepAndAlarm
                + "\nlcdOnTimeSec:" + danaPump.lcdOnTimeSec
                + "\nbacklight:" + danaPump.backlightOnTimeSec
                + "\ndanaRPumpUnits:" + danaPump.units
                + "\nlowReservoir:" + danaPump.lowReservoirRate
                + "\ncannulaVolume:" + danaPump.cannulaVolume
                + "\nrefillAmount:" + danaPump.refillAmount
                + "\ntarget:" + danaPump.target
        )
        val size = if (danaPump.hwModel >= 7) 15 else 13
        val request = ByteArray(size)
        request[0] = if (danaPump.timeDisplayType24) 0.toByte() else 1.toByte()
        request[1] = if (danaPump.buttonScrollOnOff) 1.toByte() else 0.toByte()
        request[2] = (danaPump.beepAndAlarm and 0xff).toByte()
        request[3] = (danaPump.lcdOnTimeSec and 0xff).toByte()
        request[4] = (danaPump.backlightOnTimeSec and 0xff).toByte()
        request[5] = (danaPump.selectedLanguage and 0xff).toByte()
        request[6] = (danaPump.units and 0xff).toByte()
        request[7] = (danaPump.shutdownHour and 0xff).toByte()
        request[8] = (danaPump.lowReservoirRate and 0xff).toByte()
        request[9] = (danaPump.cannulaVolume and 0xff).toByte()
        request[10] = (danaPump.cannulaVolume ushr 8 and 0xff).toByte()
        request[11] = (danaPump.refillAmount and 0xff).toByte()
        request[12] = (danaPump.refillAmount ushr 8 and 0xff).toByte()
        if (danaPump.hwModel >= 7) {
            request[13] = (danaPump.target and 0xff).toByte()
            request[14] = (danaPump.target ushr 8 and 0xff).toByte()
        }
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        @Suppress("LiftReturnOrAssignment")
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
    }

    override val friendlyName: String = "OPTION__SET_USER_OPTION"
}