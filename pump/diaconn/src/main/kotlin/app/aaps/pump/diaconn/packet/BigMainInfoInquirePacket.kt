package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.core.interfaces.di.MetroMemberInjector
import javax.inject.Inject

/**

 * BigMainInfoInquirePacket
 */
class BigMainInfoInquirePacket(
    injector: MetroMemberInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x73
        aapsLogger.debug(LTag.PUMPCOMM, "BigMainInfoInquirePacket init")

    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)

        return suffixEncode(buffer)
    }

    override val friendlyName = "PUMP_BIG_MAIN_INFO_INQUIRE"
}