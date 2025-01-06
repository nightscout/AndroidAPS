package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import dagger.android.HasAndroidInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * InjectionProgressReportPacket
 */
class InjectionProgressReportPacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper

    init {
        msgType = 0xEA.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionProgressReportPacket init ")
    }

    override fun handleMessage(data: ByteArray) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "InjectionProgressReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val setAmount = getShortToInt(bufferData) / 100.0
        val injAmount = getShortToInt(bufferData) / 100.0
        val speed = getByteToInt(bufferData)
        val injProgress = getByteToInt(bufferData)

        diaconnG8Pump.bolusingSetAmount = setAmount
        diaconnG8Pump.bolusingInjAmount = injAmount
        diaconnG8Pump.bolusingSpeed = speed
        diaconnG8Pump.bolusingInjProgress = injProgress

        aapsLogger.debug(LTag.PUMPCOMM, "bolusingSetAmount --> ${diaconnG8Pump.bolusingSetAmount}")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusingInjAmount --> ${diaconnG8Pump.bolusingInjAmount}")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusingSpeed --> ${diaconnG8Pump.bolusingSpeed}")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusingInjProgress --> ${diaconnG8Pump.bolusingInjProgress}")
    }

    override val friendlyName = "PUMP_INJECTION_PROGRESS_REPORT"
}