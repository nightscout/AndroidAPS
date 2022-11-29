package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

/**
 * InjectionBasalSettingPacket
 */
class InjectionBasalSettingPacket(
    injector: HasAndroidInjector,
    private val pattern:Int
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    init {
        msgType = 0x0C.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionBasalSettingPacket init ")
    }

    override fun encode(msgSeq:Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.put(pattern.toByte())
        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_INJECTION_BASAL_SETTING"
    }
}