package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

/**
 * InjectionExtendedBolusSettingPacket
 */
class InjectionExtendedBolusSettingPacket(
    injector: HasAndroidInjector,
    private val amount: Int,
    private val minutes: Int,
    private val bcDttm:Long
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    init {
        msgType = 0x08.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionExtendedBolusSettingPacket init ")
    }

    override fun encode(msgSeq:Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.putShort(minutes.toShort())
        buffer.putShort(amount.toShort())
        buffer.putInt(bcDttm.toInt())
        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_INJECTION_EXTENDED_BOLUS_SETTING"
    }
}