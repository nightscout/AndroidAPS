package info.nightscout.pump.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.rx.logging.LTag

class DanaRSPacketAPSBasalSetTemporaryBasal(
    injector: HasAndroidInjector,
    private var percent: Int
) : DanaRSPacket(injector) {

    var temporaryBasalRatio = 0
    var temporaryBasalDuration = 0
    var error = 0

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__APS_SET_TEMPORARY_BASAL
        aapsLogger.debug(LTag.PUMPCOMM, "New message: percent: $percent")

        if (percent < 0) percent = 0
        if (percent > 500) percent = 500
        temporaryBasalRatio = percent
        if (percent < 100) {
            temporaryBasalDuration = PARAM30MIN
            aapsLogger.debug(LTag.PUMPCOMM, "APS Temp basal start percent: $percent duration 30 min")
        } else {
            temporaryBasalDuration = PARAM15MIN
            aapsLogger.debug(LTag.PUMPCOMM, "APS Temp basal start percent: $percent duration 15 min")
        }
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(3)
        request[0] = (temporaryBasalRatio and 0xff).toByte()
        request[1] = (temporaryBasalRatio ushr 8 and 0xff).toByte()
        request[2] = (temporaryBasalDuration and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = byteArrayToInt(getBytes(data, DATA_START, 1))
        if (result != 0) {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Set APS temp basal start result: $result FAILED!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPCOMM, "Set APS temp basal start result: $result")
        }
    }

    override val friendlyName: String = "BASAL__APS_SET_TEMPORARY_BASAL"

    companion object {

        const val PARAM30MIN = 160
        const val PARAM15MIN = 150
    }
}