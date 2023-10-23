package info.nightscout.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.pump.dana.DanaPump
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import javax.inject.Inject

class DanaRSPacketAPSSetEventHistory(
    injector: HasAndroidInjector,
    private var packetType: Int,
    private var time: Long,
    private var param1: Int,
    private var param2: Int
) : DanaRSPacket(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY
        if ((packetType == DanaPump.HistoryEntry.CARBS.value || packetType == DanaPump.HistoryEntry.BOLUS.value) && param1 <= 0) param1 = 0
        aapsLogger.debug(LTag.PUMPCOMM, "Set history entry: " + dateUtil.dateAndTimeAndSecondsString(time) + " type: " + packetType + " param1: " + param1 + " param2: " + param2)
    }

    override fun getRequestParams(): ByteArray {
        val date =
            if (danaPump.usingUTC) DateTime(time).withZone(DateTimeZone.UTC)
            else DateTime(time)
        val request = ByteArray(11)
        request[0] = (packetType and 0xff).toByte()
        request[1] = (date.year - 2000 and 0xff).toByte()
        request[2] = (date.monthOfYear and 0xff).toByte()
        request[3] = (date.dayOfMonth and 0xff).toByte()
        request[4] = (date.hourOfDay and 0xff).toByte()
        request[5] = (date.minuteOfHour and 0xff).toByte()
        request[6] = (date.secondOfMinute and 0xff).toByte()
        request[7] = (param1 ushr 8 and 0xff).toByte()
        request[8] = (param1 and 0xff).toByte()
        request[9] = (param2 ushr 8 and 0xff).toByte()
        request[10] = (param2 and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        if (result != 0) {
            failed = true
            aapsLogger.error(LTag.PUMPCOMM, "Set history entry result: $result FAILED!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPCOMM, "Set history entry result: $result")
        }
    }

    override val friendlyName: String = "APS_SET_EVENT_HISTORY"
}