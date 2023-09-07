package info.nightscout.pump.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.profile.Profile
import info.nightscout.pump.dana.DanaPump
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ProfileUtil
import javax.inject.Inject
import kotlin.math.round

class DanaRSPacketBolusSet24CIRCFArray(
    injector: HasAndroidInjector,
    private val profile: Profile?
) : DanaRSPacket(injector) {

    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var profileUtil: ProfileUtil

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_24_CIR_CF_ARRAY
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(96)
        profile ?: return request // profile is null only in hash table
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