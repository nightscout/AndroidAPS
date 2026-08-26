package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.core.interfaces.di.MetroMemberInjector
import javax.inject.Inject

/**
 * InjectionSnackInquirePacket
 */
class InjectionSnackInquirePacket(injector: MetroMemberInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x47.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionSnackInquirePacket init ")
    }

    override fun encode(msgSeq: Int): ByteArray {
        return suffixEncode(prefixEncode(msgType, msgSeq, MSG_CON_END))
    }

    override val friendlyName = "PUMP_INJECTION_SNACK_INQUIRE"
}