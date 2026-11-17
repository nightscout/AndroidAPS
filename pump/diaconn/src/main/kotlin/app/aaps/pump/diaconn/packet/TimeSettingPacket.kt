package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import dev.zacsweers.metro.Inject
import org.joda.time.DateTime

/**
 * TimeSettingPacket Request Packet. The pump keeps the local time of the phone at [time].
 */
class TimeSettingPacket(
    injector: MetroMemberInjector,
    private var time: Long = 0
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x0F
        aapsLogger.debug(LTag.PUMPCOMM, "Time Sync between Phone and Pump")

    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        // The local fields, as Dana RS sets its clock. The UTC hour plus the whole hours of the zone gave hour 24 and the day
        // before around midnight, a negative hour west of UTC, and 30 minutes off in a zone such as India.
        val date = DateTime(time)
        buffer.put((date.year - 2000 and 0xff).toByte())
        buffer.put((date.monthOfYear and 0xff).toByte())
        buffer.put((date.dayOfMonth and 0xff).toByte())
        buffer.put((date.hourOfDay and 0xff).toByte())
        buffer.put((date.minuteOfHour and 0xff).toByte())
        buffer.put((date.secondOfMinute and 0xff).toByte())

        return suffixEncode(buffer)
    }

    override val friendlyName = "PUMP_TIME_SETTING_REQUEST"
}