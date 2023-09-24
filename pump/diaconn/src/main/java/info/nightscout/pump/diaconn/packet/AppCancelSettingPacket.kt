package info.nightscout.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * AppCancelSettingPacket
 */
class AppCancelSettingPacket(
    injector: HasAndroidInjector,
    private var reqMsgType: Byte,
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x29
        aapsLogger.debug(LTag.PUMPCOMM, "AppCancelSettingPacket init")
    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.put(reqMsgType) // 명령코드
        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_APP_CANCEL_SETTING"
    }
}