package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.androidaps.logging.LTag
import javax.inject.Inject

/**
 * InjectionMealSettingPacket
 */
class InjectionMealSettingPacket(
    injector: HasAndroidInjector,
    private val amount:Int,
    private val bcDttm:Long
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    init {
        msgType = 0x06.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionMealSettingPacket init ")
    }

    override fun encode(msgSeq:Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END);
        buffer.putShort(amount.toShort())
        buffer.putLong(bcDttm)
        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_INJECTION_MEAL_SETTING"
    }
}