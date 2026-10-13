package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import app.aaps.pump.danars.encryption.BleEncryption

class DanaRSPacketBolusSetBolusOption(
    injector: HasAndroidInjector,
    private var extendedBolusOptionOnOff: Int = 0,
    private var bolusCalculationOption: Int = 0,
    private var missedBolusConfig: Int = 0,
    private var missedBolus01StartHour: Int = 0,
    private var missedBolus01StartMin: Int = 0,
    private var missedBolus01EndHour: Int = 0,
    private var missedBolus01EndMin: Int = 0,
    private var missedBolus02StartHour: Int = 0,
    private var missedBolus02StartMin: Int = 0,
    private var missedBolus02EndHour: Int = 0,
    private var missedBolus02EndMin: Int = 0,
    private var missedBolus03StartHour: Int = 0,
    private var missedBolus03StartMin: Int = 0,
    private var missedBolus03EndHour: Int = 0,
    private var missedBolus03EndMin: Int = 0,
    private var missedBolus04StartHour: Int = 0,
    private var missedBolus04StartMin: Int = 0,
    private var missedBolus04EndHour: Int = 0,
    private var missedBolus04EndMin: Int = 0

) : DanaRSPacket(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_BOLUS_OPTION
        aapsLogger.debug(LTag.PUMPCOMM, "Setting bolus options")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(19)
        request[0] = (extendedBolusOptionOnOff and 0xff).toByte()
        request[1] = (bolusCalculationOption and 0xff).toByte()
        request[2] = (missedBolusConfig and 0xff).toByte()
        request[3] = (missedBolus01StartHour and 0xff).toByte()
        request[4] = (missedBolus01StartMin and 0xff).toByte()
        request[5] = (missedBolus01EndHour and 0xff).toByte()
        request[6] = (missedBolus01EndMin and 0xff).toByte()
        request[7] = (missedBolus02StartHour and 0xff).toByte()
        request[8] = (missedBolus02StartMin and 0xff).toByte()
        request[9] = (missedBolus02EndHour and 0xff).toByte()
        request[10] = (missedBolus02EndMin and 0xff).toByte()
        request[11] = (missedBolus03StartHour and 0xff).toByte()
        request[12] = (missedBolus03StartMin and 0xff).toByte()
        request[13] = (missedBolus03EndHour and 0xff).toByte()
        request[14] = (missedBolus03EndMin and 0xff).toByte()
        request[15] = (missedBolus04StartHour and 0xff).toByte()
        request[16] = (missedBolus04StartMin and 0xff).toByte()
        request[17] = (missedBolus04EndHour and 0xff).toByte()
        request[18] = (missedBolus04EndMin and 0xff).toByte()
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

    override val friendlyName: String = "BOLUS__SET_BOLUS_OPTION"
}