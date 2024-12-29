package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * InjectionSnackInquireResponsePacket
 */
@Suppress("SpellCheckingInspection")
class InjectionSnackInquireResponsePacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x87.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionSnackInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray) {
        val result = defect(data)
        if (result != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "InjectionSnackInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result2 = getByteToInt(bufferData)

        if (!isSuccInquireResponseResult(result2)) {
            failed = true
            return
        }

        diaconnG8Pump.snackStatus = getByteToInt(bufferData) //주입상태
        diaconnG8Pump.snackAmount = getShortToInt(bufferData) / 100.0 // 주입설정량
        diaconnG8Pump.snackInjAmount = getShortToInt(bufferData) / 100.0 // 현재주입량
        diaconnG8Pump.snackSpeed = getByteToInt(bufferData) //주입속도

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> $result2")
        aapsLogger.debug(LTag.PUMPCOMM, "snackStatus > " + diaconnG8Pump.snackStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "snackAmount > " + diaconnG8Pump.snackAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "snackInjAmount > " + diaconnG8Pump.snackInjAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "snackSpeed > " + diaconnG8Pump.snackSpeed)
    }

    override val friendlyName = "PUMP_INJECTION_SNACK_INQUIRE_RESPONSE"
}