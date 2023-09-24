package info.nightscout.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * BatteryWarningReportPacket
 */
class BatteryWarningReportPacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0xD7.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BatteryWarningReportPacket init ")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "BatteryWarningReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        diaconnG8Pump.batteryWaningGrade = getByteToInt(bufferData)
        diaconnG8Pump.batteryWaningProcess = getByteToInt(bufferData)
        diaconnG8Pump.batteryWaningRemain = getByteToInt(bufferData)

        aapsLogger.debug(LTag.PUMPCOMM, "batteryWaningGrade --> ${diaconnG8Pump.batteryWaningGrade} (1:info, 2: warning , 3: major , 4: critical)")
        aapsLogger.debug(LTag.PUMPCOMM, "batteryWaningProcess --> ${diaconnG8Pump.batteryWaningProcess} (1:skip, 2: stop , 3: ignore ) ")
        aapsLogger.debug(LTag.PUMPCOMM, "batteryWaningRemain --> ${diaconnG8Pump.batteryWaningRemain}   (0~100%) )")

    }

    override fun getFriendlyName(): String {
        return "PUMP_BATTERY_WARNING_REPORT"
    }
}