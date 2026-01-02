package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketBasalSetProfileBasalRate @Inject constructor(
    private val aapsLogger: AAPSLogger
) : DanaRSPacket() {

    private var profileNumber: Int = 0
    private lateinit var profileBasalRate: Array<Double>

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_BASAL_RATE
        aapsLogger.debug(LTag.PUMPCOMM, "Setting new basal rates for profile $profileNumber")
    }

    fun with(profileNumber: Int, profileBasalRate: Array<Double>) = this.also {
        this.profileNumber = profileNumber
        this.profileBasalRate = profileBasalRate
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(49)
        request[0] = (profileNumber and 0xff).toByte()
        var i = 0
        val size = 24
        while (i < size) {
            val rate = (profileBasalRate[i] * 100.0).toInt()
            request[1 + i * 2] = (rate and 0xff).toByte()
            request[2 + i * 2] = (rate ushr 8 and 0xff).toByte()
            i++
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

    override val friendlyName: String = "BASAL__SET_PROFILE_BASAL_RATE"
}