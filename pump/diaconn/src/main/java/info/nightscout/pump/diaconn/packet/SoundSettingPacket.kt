package info.nightscout.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * SoundSettingPacket
 */
class SoundSettingPacket(
    injector: HasAndroidInjector,
    private var type: Int, //
    private var intensity: Int //
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x0D
        aapsLogger.debug(LTag.PUMPCOMM, "SoundSettingPacket init")
    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.put(type.toByte()) // 명령코드
        buffer.put(intensity.toByte()) // 명령코드
        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_SOUND_SETTING"
    }
}