package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import dev.zacsweers.metro.Inject

/**
 * Answer to [TimeInquirePacket]: the pump clock. It is passed to [DiaconnG8Pump.readPumpClock].
 */
class TimeInquireResponsePacket(
    injector: MetroMemberInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x8F.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "TimeInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "TimeInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result = getByteToInt(bufferData)
        if (!isSuccInquireResponseResult(result)) {
            failed = true
            return
        }
        // year (18~99), month, day, hour, minute, second. It took only the fields, so the driver compared the phone with the
        // pump time of the main info read earlier, moved on by every message since.
        diaconnG8Pump.readPumpClock(
            year = getByteToInt(bufferData) + 2000,
            month = getByteToInt(bufferData),
            day = getByteToInt(bufferData),
            hour = getByteToInt(bufferData),
            minute = getByteToInt(bufferData),
            second = getByteToInt(bufferData)
        )
    }

    override val friendlyName = "PUMP_TIME_INQUIRE_RESPONSE"
}