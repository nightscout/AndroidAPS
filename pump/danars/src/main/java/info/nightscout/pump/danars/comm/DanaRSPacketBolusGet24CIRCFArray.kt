package info.nightscout.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.pump.dana.DanaPump
import javax.inject.Inject

class DanaRSPacketBolusGet24CIRCFArray(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_24_CIR_CF_ARRAY
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        danaPump.units = byteArrayToInt(getBytes(data, DATA_START, 1))
        for (i in 0..23) {
            val cir = byteArrayToInt(getBytes(data, DATA_START + 1 + 2 * i, 2)).toDouble()
            val cf = if (danaPump.units == DanaPump.UNITS_MGDL)
                byteArrayToInt(getBytes(data, DATA_START + 1 + 48 + 2 * i, 2)).toDouble()
            else
                byteArrayToInt(getBytes(data, DATA_START + 1 + 48 + 2 * i, 2)) / 100.0
            danaPump.cir24[i] = cir
            danaPump.cf24[i] = cf
            aapsLogger.debug(LTag.PUMPCOMM, "$i: CIR: $cir  CF: $cf")
        }
        if (danaPump.units < 0 || danaPump.units > 1) failed = true
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units: " + if (danaPump.units == DanaPump.UNITS_MGDL) "MGDL" else "MMOL")
    }

    override val friendlyName = "BOLUS__GET_24_ CIR_CF_ARRAY"
}