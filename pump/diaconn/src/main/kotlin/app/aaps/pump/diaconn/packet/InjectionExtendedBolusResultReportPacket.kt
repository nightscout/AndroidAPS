package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import dagger.android.HasAndroidInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * InjectionExtendedBolusResultReportPacket
 */
class InjectionExtendedBolusResultReportPacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper

    init {
        msgType = 0xe5.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionExtendedBolusResultReportPacket init ")
    }

    override fun handleMessage(data: ByteArray) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "InjectionExtendedBolusResultReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)

        val result = getByteToInt(bufferData) // 0: success , 1: user stop, 2:fail
        val settingMinutes = getShortToInt(bufferData)
        val elapsedTime = getShortToInt(bufferData)
        val bolusAmountToBeDelivered = getShortToInt(bufferData) / 100.0
        val deliveredBolusAmount = getShortToInt(bufferData) / 100.0

        diaconnG8Pump.squareStatus = if (result == 0) 1 else 2 // 1=in progress, 2=stopped
        diaconnG8Pump.squareTime = settingMinutes
        diaconnG8Pump.squareInjTime = elapsedTime
        diaconnG8Pump.squareAmount = bolusAmountToBeDelivered
        diaconnG8Pump.squareInjAmount = deliveredBolusAmount

        aapsLogger.debug(LTag.PUMPCOMM, "Result: $result")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus running: " + diaconnG8Pump.squareAmount + " U/h")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus duration: " + diaconnG8Pump.squareTime + " min")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus so far: " + diaconnG8Pump.squareInjTime + " min")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus delivered so far: " + diaconnG8Pump.squareInjAmount + " U")
    }

    override val friendlyName = "PUMP_INJECTION_EXTENDED_BOLUS_RESULT_REPORT"
}