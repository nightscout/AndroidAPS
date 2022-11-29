package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import javax.inject.Inject

/**
 * TimeSettingPacket Request Packet
 */
class TimeSettingPacket(
    injector: HasAndroidInjector,
    private var time: Long = 0,
    private var offset:Int = 0
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x0F
        aapsLogger.debug(LTag.PUMPCOMM, "Time Sync between Phone and Pump")

    }

    override fun encode(msgSeq:Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        val date = DateTime(time).withZone(DateTimeZone.UTC)
        buffer.put((date.year - 2000 and 0xff).toByte())
        buffer.put((date.monthOfYear and 0xff).toByte())
        buffer.put((date.dayOfMonth and 0xff).toByte())
        buffer.put((date.hourOfDay + offset and 0xff).toByte())
        buffer.put((date.minuteOfHour and 0xff).toByte())
        buffer.put((date.secondOfMinute and 0xff).toByte())

        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_TIME_SETTING_REQUEST"
    }
}