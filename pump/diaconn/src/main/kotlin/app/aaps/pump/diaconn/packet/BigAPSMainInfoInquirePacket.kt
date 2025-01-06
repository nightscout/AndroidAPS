package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**

 * BigAPSMainInfoInquirePacket
 */
class BigAPSMainInfoInquirePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x54
        aapsLogger.debug(LTag.PUMPCOMM, "BigAPSMainInfoInquirePacket init")

    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)

        return suffixEncode(buffer)
    }

    override val friendlyName = "PUMP_BIG_APS_MAIN_INFO_INQUIRE"
}