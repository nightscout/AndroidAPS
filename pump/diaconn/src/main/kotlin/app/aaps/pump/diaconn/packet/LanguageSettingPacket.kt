package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * LanguageSettingPacket
 */
@Suppress("SpellCheckingInspection")
class LanguageSettingPacket(
    injector: HasAndroidInjector,
    private var type: Int
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x20
        aapsLogger.debug(LTag.PUMPCOMM, "LanguageSettingPacket init")
    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.put(type.toByte()) // 명령코드
        return suffixEncode(buffer)
    }

    override val friendlyName = "PUMP_LANGUAGE_SETTING"
}