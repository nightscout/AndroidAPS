package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.danars.encryption.BleEncryption
import org.joda.time.DateTime
import javax.inject.Inject

class DanaRSPacketOptionSetPumpTime @Inject constructor(
    private val aapsLogger: AAPSLogger,
    dateUtil: DateUtil
) : DanaRSPacket() {

    private var time: Long = 0
    var error = 0

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_OPTION__SET_PUMP_TIME
        aapsLogger.debug(LTag.PUMPCOMM, "Setting pump time " + dateUtil.dateAndTimeAndSecondsString(time))
    }

    fun with(time: Long) = this.also { this.time = time }

    override fun getRequestParams(): ByteArray {
        val date = DateTime(time)
        val request = ByteArray(6)
        request[0] = (date.year - 2000 and 0xff).toByte()
        request[1] = (date.monthOfYear and 0xff).toByte()
        request[2] = (date.dayOfMonth and 0xff).toByte()
        request[3] = (date.hourOfDay and 0xff).toByte()
        request[4] = (date.minuteOfHour and 0xff).toByte()
        request[5] = (date.secondOfMinute and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        @Suppress("LiftReturnOrAssignment")
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
    }

    override val friendlyName: String = "OPTION__SET_PUMP_TIME"
}