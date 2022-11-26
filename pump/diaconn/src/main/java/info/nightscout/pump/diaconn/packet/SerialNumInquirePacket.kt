package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

/**

 * SerialNumInquirePacket
 */
class SerialNumInquirePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    init {
        msgType = 0x6E
        aapsLogger.debug(LTag.PUMPCOMM, "SeialNumInquirePacket init")
    }

    override fun encode(msgSeq:Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_SERIAL_NUM_INQUIRE"
    }
}