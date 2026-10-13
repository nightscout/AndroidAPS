package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.pump.diaconn.DiaconnG8Pump
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * InjectionSnackResultReportPacket
 */
@Suppress("SpellCheckingInspection")
class InjectionSnackResultReportPacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper

    init {
        msgType = 0xe4.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionSnackResultReportPacket init ")
    }

    override fun handleMessage(data: ByteArray) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "InjectionSnackResultReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result = getByteToInt(bufferData)
        val bolusAmountToBeDelivered = getShortToInt(bufferData) / 100.0
        val deliveredBolusAmount = getShortToInt(bufferData) / 100.0

        diaconnG8Pump.lastBolusAmount = deliveredBolusAmount
        diaconnG8Pump.lastBolusTime = dateUtil.now()
        BolusProgressData.delivered = deliveredBolusAmount

        if (result == 1) {
            diaconnG8Pump.bolusStopped = true // 주입 중 취소 처리!
        }
        diaconnG8Pump.bolusDone = true // 주입완료 처리!

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> $result")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusAmountToBeDelivered --> $bolusAmountToBeDelivered")
        aapsLogger.debug(LTag.PUMPCOMM, "lastBolusAmount --> ${diaconnG8Pump.lastBolusAmount}")
        aapsLogger.debug(LTag.PUMPCOMM, "lastBolusTime --> ${diaconnG8Pump.lastBolusTime}")
        aapsLogger.debug(LTag.PUMPCOMM, "delivered --> ${BolusProgressData.delivered}")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusDone --> ${diaconnG8Pump.bolusDone}")
    }

    override val friendlyName = "PUMP_INJECTION_SNACK_REPORT"
}