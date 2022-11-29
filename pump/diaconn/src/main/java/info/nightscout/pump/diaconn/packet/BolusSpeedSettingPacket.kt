package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

/**
 * BolusSpeedSettingPacket
 */
class BolusSpeedSettingPacket(
    injector: HasAndroidInjector,
    private var type: Int
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x05
        aapsLogger.debug(LTag.PUMPCOMM, "BolusSpeedSettingPacket init")
    }

    override fun encode(msgSeq:Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.put(type.toByte()) // 명령코드
        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_BOLUS_SPEED_SETTING_PACKET"
    }
}