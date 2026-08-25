package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.core.interfaces.di.MetroMemberInjector
import javax.inject.Inject

/**
 * InjectionBasalSettingPacket
 */
class InjectionBasalSettingPacket(
    injector: MetroMemberInjector,
    private val pattern: Int
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x0C.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionBasalSettingPacket init ")
    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.put(pattern.toByte())
        return suffixEncode(buffer)
    }

    override val friendlyName = "PUMP_INJECTION_BASAL_SETTING"
}