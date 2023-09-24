package info.nightscout.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
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

    override fun getFriendlyName(): String {
        return "PUMP_BIG_APS_MAIN_INFO_INQUIRE"
    }
}