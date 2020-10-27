package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.logging.LTag
import javax.inject.Inject

class DanaRS_Packet_Bolus_Set_24_CIR_CF_Array(
    injector: HasAndroidInjector,
    private val profile: Profile?
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

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
            if (danaPump.units == DanaPump.UNITS_MMOL) isf *= 10
            val ic = profile.getIcTimeFromMidnight(i * 3600) * 100
            request[2 * i] = (isf.toInt() and 0xff).toByte()
            request[2 * i] = (isf.toInt() ushr 8 and 0xff).toByte()
            request[cfStart + 2 * i] = (ic.toInt() and 0xff).toByte()
            request[cfStart + 2 * i] = (ic.toInt() ushr 8 and 0xff).toByte()
        }
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
    }

    override fun getFriendlyName(): String {
        return "BOLUS__SET_24_CIR_CF_ARRAY"
    }
}