package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.danars.encryption.BleEncryption
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import javax.inject.Inject

class DanaRSPacketOptionSetPumpUTCAndTimeZone @Inject constructor(
    private val aapsLogger: AAPSLogger,
    dateUtil: DateUtil
) : DanaRSPacket() {

    private var time: Long = 0
    private var zoneOffset: Int = 0
    var error = 0

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_OPTION__SET_PUMP_UTC_AND_TIME_ZONE
        aapsLogger.debug(LTag.PUMPCOMM, "Setting UTC pump time ${dateUtil.dateAndTimeAndSecondsString(time)} ZoneOffset: $zoneOffset")
    }

    fun with(time: Long, zoneOffset: Int) = this.also {
        this.time = time
        this.zoneOffset = zoneOffset
    }

    override fun getRequestParams(): ByteArray {
        val date = DateTime(time).withZone(DateTimeZone.UTC)
        val request = ByteArray(7)
        request[0] = (date.year - 2000 and 0xff).toByte()
        request[1] = (date.monthOfYear and 0xff).toByte()
        request[2] = (date.dayOfMonth and 0xff).toByte()
        request[3] = (date.hourOfDay and 0xff).toByte()
        request[4] = (date.minuteOfHour and 0xff).toByte()
        request[5] = (date.secondOfMinute and 0xff).toByte()
        request[6] = zoneOffset.toByte()
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

    override val friendlyName: String = "OPTION__SET_PUMP_UTC_AND_TIMEZONE"
}