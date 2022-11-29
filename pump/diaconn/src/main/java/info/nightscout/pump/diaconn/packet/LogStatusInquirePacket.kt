package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

/**
 * LogStatusInquirePacket
 */
class LogStatusInquirePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    init {
        msgType = 0x56
        aapsLogger.debug(LTag.PUMPCOMM, "LogStatusInquirePacket INIT")
    }

    override fun encode(msgSeq:Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_LOG_STATUS_INQUIRE"
    }
}