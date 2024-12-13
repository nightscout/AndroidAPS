package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * InjectionMealSettingPacket
 */
class InjectionMealSettingPacket(
    injector: HasAndroidInjector,
    private val amount: Int,
    private val bcDttm: Long
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x06.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionMealSettingPacket init ")
    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.putShort(amount.toShort())
        buffer.putLong(bcDttm)
        return suffixEncode(buffer)
    }

    override val friendlyName = "PUMP_INJECTION_MEAL_SETTING"
}