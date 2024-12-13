package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * TempBasalInquirePacket
 */
class TempBasalInquirePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x4A
        aapsLogger.debug(LTag.PUMPCOMM, "TempBasalInquirePacket Init")
    }

    override fun encode(msgSeq: Int): ByteArray {
        return suffixEncode(prefixEncode(msgType, msgSeq, MSG_CON_END))
    }

    override val friendlyName = "PUMP_TEMP_BASAL_INQUIRE_REQUEST"
}