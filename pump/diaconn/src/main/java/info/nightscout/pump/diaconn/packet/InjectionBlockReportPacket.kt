package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

/**
 * InjectionBlockReportPacket
 */
class InjectionBlockReportPacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0xD8.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionBlockReportPacket init ")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "InjectionBasalReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        diaconnG8Pump.injectionBlockGrade = getByteToInt(bufferData)
        diaconnG8Pump.injectionBlockProcess = getByteToInt(bufferData)
        diaconnG8Pump.injectionBlockRemainAmount = getShortToInt(bufferData) / 100.0
        diaconnG8Pump.injectionBlockType =  getByteToInt(bufferData)

        aapsLogger.debug(LTag.PUMPCOMM, "injectionBlockGrade --> ${diaconnG8Pump.injectionBlockGrade} (1:info, 2: warning , 3: major , 4: critical)")
        aapsLogger.debug(LTag.PUMPCOMM, "injectionBlockProcess --> ${diaconnG8Pump.injectionBlockProcess} (1:skip, 2: stop , 3: ignore ) ")
        aapsLogger.debug(LTag.PUMPCOMM, "injectionBlockReaminAmount --> ${diaconnG8Pump.injectionBlockRemainAmount}  ")
        aapsLogger.debug(LTag.PUMPCOMM, "injectionBlockType --> ${diaconnG8Pump.injectionBlockType} (1:basal, 2: meal , 3: normal , 4: square , 5:dual, 6:tube, 7:needle) ")
    }

    override fun getFriendlyName(): String {
        return "PUMP_INJECTION_BLOCK_REPORT"
    }
}