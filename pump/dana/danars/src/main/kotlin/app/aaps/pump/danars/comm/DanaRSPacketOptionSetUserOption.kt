package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import dev.zacsweers.metro.Inject

@Inject
class DanaRSPacketOptionSetUserOption(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_OPTION__SET_USER_OPTION
        aapsLogger.debug(LTag.PUMPCOMM, "Setting user settings")
    }

    /** The user options this write sends, for `DanaRSService.setUserSettings` to keep once the pump took them. */
    var written: DanaPump.UserOptions? = null
        private set

    override fun getRequestParams(): ByteArray {
        val options = danaPump.userOptionsForWrite().also { written = it }
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "UserOptions:" + (System.currentTimeMillis() - danaPump.lastConnection) / 1000 + " s ago"
                + "\n" + options
                + "\ncannulaVolume:" + danaPump.cannulaVolume
                + "\nrefillAmount:" + danaPump.refillAmount
                + "\ntarget:" + danaPump.target
        )
        val size = when {
            danaPump.isDanaI2     -> 16 // + AutoLock
            danaPump.hwModel >= 7 -> 15 // + Target BG
            else                  -> 13
        }
        val request = ByteArray(size)
        request[0] = if (options.timeDisplayType24) 0.toByte() else 1.toByte()
        request[1] = if (options.buttonScrollOnOff) 1.toByte() else 0.toByte()
        request[2] = (options.beepAndAlarm and 0xff).toByte()
        request[3] = (options.lcdOnTimeSec and 0xff).toByte()
        request[4] = (options.backlightOnTimeSec and 0xff).toByte()
        request[5] = (danaPump.selectedLanguage and 0xff).toByte()
        request[6] = (options.units and 0xff).toByte()
        request[7] = (options.shutdownHour and 0xff).toByte()
        request[8] = (options.lowReservoirRate and 0xff).toByte()
        request[9] = (danaPump.cannulaVolume and 0xff).toByte()
        request[10] = (danaPump.cannulaVolume ushr 8 and 0xff).toByte()
        request[11] = (danaPump.refillAmount and 0xff).toByte()
        request[12] = (danaPump.refillAmount ushr 8 and 0xff).toByte()
        if (danaPump.hwModel >= 7) {
            request[13] = (danaPump.target and 0xff).toByte()
            request[14] = (danaPump.target ushr 8 and 0xff).toByte()
        }
        if (danaPump.isDanaI2)
            request[15] = if (options.autoLock) 1.toByte() else 0.toByte()
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