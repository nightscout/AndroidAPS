package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import dev.zacsweers.metro.Inject

/**
 * The pump reports the end of an extended bolus: given in full, stopped by the user, or failed.
 */
class InjectionExtendedBolusResultReportPacket(injector: MetroMemberInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

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

        val result = getByteToInt(bufferData) // 0: given in full, 1: user stop, 2: fail
        val settingMinutes = getShortToInt(bufferData)
        val elapsedTime = getShortToInt(bufferData)
        val bolusAmountToBeDelivered = getShortToInt(bufferData) / 100.0
        val deliveredBolusAmount = getShortToInt(bufferData) / 100.0

        // Every result is the end of the extended bolus. Result 0 ("given in full") was read as "running". A real G8
        // (fw 2.50, 2026-11-11) sent result 1 for a stop.
        diaconnG8Pump.squareStatus = SQUARE_ENDED
        diaconnG8Pump.squareTime = settingMinutes
        diaconnG8Pump.squareInjTime = elapsedTime
        diaconnG8Pump.squareAmount = bolusAmountToBeDelivered
        diaconnG8Pump.squareInjAmount = deliveredBolusAmount

        aapsLogger.debug(
            LTag.PUMPCOMM,
            "Extended bolus ended, result $result: $deliveredBolusAmount of $bolusAmountToBeDelivered U in $elapsedTime of $settingMinutes min"
        )
    }

    override val friendlyName = "PUMP_INJECTION_EXTENDED_BOLUS_RESULT_REPORT"

    private companion object {

        /** The square (extended) bolus status of the main info when none runs. */
        const val SQUARE_ENDED = 2
    }
}