package info.nightscout.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * InjectionCancelSettingPacket
 */
class InjectionCancelSettingPacket(
    injector: HasAndroidInjector,
    private var reqMsgType: Byte, // 명령코드
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x2B
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionCancelSettingPacket INIT")
    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.put(reqMsgType)
        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_INJECTION_CANCEL_SETTING_REQUEST"
    }
}