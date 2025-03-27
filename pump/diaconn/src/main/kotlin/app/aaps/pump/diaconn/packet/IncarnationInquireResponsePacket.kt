package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.diaconn.DiaconnG8Pump
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * IncarnationInquireResponsePacket
 */
@Suppress("SpellCheckingInspection")
open class IncarnationInquireResponsePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var rh: ResourceHelper

    var result = 0

    init {
        msgType = 0xBA.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "IncarnationInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "IncarnationInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        result = getByteToInt(bufferData)
        if (!isSuccInquireResponseResult(result)) {
            failed = true
            return
        }
        // 5. 로그상태 조회
        diaconnG8Pump.pumpIncarnationNum = getShortToInt(bufferData) // 펌프 Incarnation 번호
        aapsLogger.debug(LTag.PUMPCOMM, "Result --> $result")
        aapsLogger.debug(LTag.PUMPCOMM, "pumpIncarnationNum > " + diaconnG8Pump.pumpIncarnationNum)
    }

    override val friendlyName = "PUMP_INCARNATION_INQUIRE_RESPONSE"
}