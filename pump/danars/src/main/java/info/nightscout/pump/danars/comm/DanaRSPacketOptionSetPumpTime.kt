package info.nightscout.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import org.joda.time.DateTime

class DanaRSPacketOptionSetPumpTime(
    injector: HasAndroidInjector,
    private var time: Long = 0
) : DanaRSPacket(injector) {

    var error = 0

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_OPTION__SET_PUMP_TIME
        aapsLogger.debug(LTag.PUMPCOMM, "Setting pump time " + dateUtil.dateAndTimeAndSecondsString(time))
    }

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