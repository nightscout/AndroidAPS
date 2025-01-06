package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * SnackLimitInquireResponsePacket
 */
class SnackLimitInquireResponsePacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x90.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "SnackLimitInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray) {
        val result = defect(data)
        if (result != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "SnackLimitInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result2 = getByteToInt(bufferData)
        if (!isSuccInquireResponseResult(result2)) {
            failed = true
            return
        }

        diaconnG8Pump.maxBolus = getShortToInt(bufferData).toDouble() / 100
        diaconnG8Pump.maxBolusePerDay = getShortToInt(bufferData).toDouble() / 100

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> ${diaconnG8Pump.result}")
        aapsLogger.debug(LTag.PUMPCOMM, "maxBolusesPerDay --> ${diaconnG8Pump.maxBolusePerDay}")
        aapsLogger.debug(LTag.PUMPCOMM, "maxBolus --> ${diaconnG8Pump.maxBolus}")
    }

    override val friendlyName = "PUMP_SNACK_LIMIT_INQUIRE_RESPONSE"
}
