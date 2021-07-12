package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.androidaps.logging.LTag
import javax.inject.Inject

/**
 * TempBasalInquirePacket
 */
class TempBasalInquirePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x4A
        aapsLogger.debug(LTag.PUMPCOMM, "TempBasalInquirePacket Init")
    }

    override fun encode(msgSeq:Int): ByteArray {
        return suffixEncode(prefixEncode(msgType, msgSeq, MSG_CON_END))
    }

    override fun getFriendlyName(): String {
        return "PUMP_TEMP_BASAL_INQUIRE_REQUEST"
    }
}