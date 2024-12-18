package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * AppCancelSettingPacket
 */
@Suppress("SpellCheckingInspection")
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

    override val friendlyName = "PUMP_APP_CANCEL_SETTING"
}