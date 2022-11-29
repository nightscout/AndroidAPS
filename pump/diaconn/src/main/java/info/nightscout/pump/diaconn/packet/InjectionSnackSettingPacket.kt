package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

/**
 * InjectionSnackSettingPacket
 */
class InjectionSnackSettingPacket(
    injector: HasAndroidInjector,
    private val amount:Int
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    init {
        msgType = 0x07.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionSnackSettingPacket init ")
    }

    override fun encode(msgSeq:Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.putShort(amount.toShort())
        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_INJECTION_SNACK_SETTING"
    }
}