package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketBasalSetProfileNumber @Inject constructor(
    private val aapsLogger: AAPSLogger
) : DanaRSPacket() {

    private var profileNumber: Int = 0

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_NUMBER
        aapsLogger.debug(LTag.PUMPCOMM, "Setting profile number $profileNumber")
    }

    fun with(profileNumber: Int = 0) = this.also {
        this.profileNumber = profileNumber
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(1)
        request[0] = (profileNumber and 0xff).toByte()
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

    override val friendlyName: String = "BASAL__SET_PROFILE_NUMBER"
}