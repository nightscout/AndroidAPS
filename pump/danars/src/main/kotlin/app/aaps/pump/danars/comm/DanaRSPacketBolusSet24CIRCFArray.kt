package app.aaps.pump.danars.comm

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject
import kotlin.math.round

class DanaRSPacketBolusSet24CIRCFArray @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump,
    private val profileUtil: ProfileUtil
) : DanaRSPacket() {

    private lateinit var profile: Profile

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_24_CIR_CF_ARRAY
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    fun with(profile: Profile) = this.also { this.profile = profile }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(96)
        val cfStart = 24 * 2
        for (i in 0..23) {
            var isf = profile.getIsfMgdlTimeFromMidnight(i * 3600)
            if (danaPump.units == DanaPump.UNITS_MMOL) {
                isf = profileUtil.fromMgdlToUnits(isf, GlucoseUnit.MMOL)
                isf *= 100
            }
            val ic = profile.getIcTimeFromMidnight(i * 3600)
            request[2 * i] = (round(ic).toInt() and 0xff).toByte()
            request[2 * i + 1] = (round(ic).toInt() ushr 8 and 0xff).toByte()
            request[cfStart + 2 * i] = (round(isf).toInt() and 0xff).toByte()
            request[cfStart + 2 * i + 1] = (round(isf).toInt() ushr 8 and 0xff).toByte()
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

    override val friendlyName: String = "BOLUS__SET_24_CIR_CF_ARRAY"
}