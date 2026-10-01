package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.core.interfaces.di.MetroMemberInjector
import dev.zacsweers.metro.Inject

/**
 * Basal Pause Setting Packet
 */
class BasalPauseSettingPacket(
    injector: MetroMemberInjector,
    private var status: Int //(1:pause, 2: cancel pause)
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x03
        aapsLogger.debug(LTag.PUMPCOMM, "BasalPauseSettingPacket Init")
    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        buffer.put(status.toByte()) // (1:pause, 2: cancel pause)
        return suffixEncode(buffer)
    }

    override val friendlyName = "PUMP_BASAL_PAUSE_SETTING"
}