package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.core.interfaces.di.MetroMemberInjector
import dev.zacsweers.metro.Inject

/**
 * BolusSpeedSettingPacket
 */
@Suppress("SpellCheckingInspection")
class BolusSpeedSettingPacket(
    injector: MetroMemberInjector,
    private var type: Int
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x05
        aapsLogger.debug(LTag.PUMPCOMM, "BolusSpeedSettingPacket init")
    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.put(type.toByte()) // 명령코드
        return suffixEncode(buffer)
    }

    override val friendlyName = "PUMP_BOLUS_SPEED_SETTING_PACKET"
}