package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import javax.inject.Inject

/**
 * InjectionSnackResultReportPacket
 */
class InjectionSnackResultReportPacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper

    init {
        msgType = 0xe4.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionSnackResultReportPacket init ")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "InjectionSnackResultReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result = getByteToInt(bufferData)
        val bolusAmountToBeDelivered = getShortToInt(bufferData)/100.0
        val deliveredBolusAmount  = getShortToInt(bufferData)/100.0

        diaconnG8Pump.bolusAmountToBeDelivered = bolusAmountToBeDelivered
        diaconnG8Pump.lastBolusAmount = deliveredBolusAmount
        diaconnG8Pump.lastBolusTime = dateUtil.now()
        diaconnG8Pump.bolusingTreatment?.insulin = deliveredBolusAmount

        if(result == 1) {
            diaconnG8Pump.bolusStopped = true // 주입 중 취소 처리!
        }
        diaconnG8Pump.bolusDone = true // 주입완료 처리!

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> $result")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusAmountToBeDelivered --> ${diaconnG8Pump.bolusAmountToBeDelivered}")
        aapsLogger.debug(LTag.PUMPCOMM, "lastBolusAmount --> ${diaconnG8Pump.lastBolusAmount}")
        aapsLogger.debug(LTag.PUMPCOMM, "lastBolusTime --> ${diaconnG8Pump.lastBolusTime}")
        aapsLogger.debug(LTag.PUMPCOMM, "diaconnG8Pump.bolusingTreatment?.insulin --> ${diaconnG8Pump.bolusingTreatment?.insulin}")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusDone --> ${diaconnG8Pump.bolusDone}")
    }

    override fun getFriendlyName(): String {
        return "PUMP_INJECTION_SNACK_REPORT"

    }
}