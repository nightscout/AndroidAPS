package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

/**
 * InsulinLackReportPacket
 */
class InsulinLackReportPacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0xD8.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InsulinLackReportPacket init ")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "InsulinLackReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        diaconnG8Pump.insulinWarningGrade = getByteToInt(bufferData)
        diaconnG8Pump.insulinWarningProcess = getByteToInt(bufferData)
        diaconnG8Pump.insulinWarningRemain = getByteToInt(bufferData)

        aapsLogger.debug(LTag.PUMPCOMM, "insulinWarningGrade --> ${diaconnG8Pump.insulinWarningGrade} (1:info, 2: warning , 3: major , 4: critical)")
        aapsLogger.debug(LTag.PUMPCOMM, "insulinWarningProcess --> ${diaconnG8Pump.insulinWarningProcess} (1:skip, 2: stop , 3: ignore ) ")
        aapsLogger.debug(LTag.PUMPCOMM, "insulinWarningRemain --> ${diaconnG8Pump.insulinWarningRemain} (0~100%) ")

    }

    override fun getFriendlyName(): String {
        return "PUMP_INJECTION_LACK_REPORT"
    }
}