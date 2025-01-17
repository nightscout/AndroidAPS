package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * BasalSettingPacket
 */
@Suppress("SpellCheckingInspection")
class BasalSettingPacket(
    injector: HasAndroidInjector,
    private var pattern: Int, // pattern(1=basic, 2=life1, 3=life2, 4=life3, 5=dr1, 6=dr2)
    private var group: Int,  //hour group (1=00~05, 2=06~11, 3=12~17, 4=18~23)
    private var amount1: Int,
    private var amount2: Int,
    private var amount3: Int,
    private var amount4: Int,
    private var amount5: Int,
    private var amount6: Int
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x0B
        aapsLogger.debug(LTag.PUMPCOMM, "Setting new basal rates for profile")

    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = if (group == 4) {
            // 마지막 그룹일때
            prefixEncode(msgType, msgSeq, MSG_CON_END)
        } else {
            // 1, 2, 3 그룹일때
            prefixEncode(msgType, msgSeq, MSG_CON_CONTINUE)
        }
        buffer.put(pattern.toByte()) // 패턴 종류 (1=기본, 2=생활1, 3=생활2, 4=생활3, 5=닥터1, 6=닥터2)
        buffer.put(group.toByte()) // 그룹 (1=00~05, 2=06~11, 3=12~17, 4=18~23)
        buffer.putShort(amount1.toShort()) // 주입량 1
        buffer.putShort(amount2.toShort()) // 주입량 2
        buffer.putShort(amount3.toShort()) // 주입량 3
        buffer.putShort(amount4.toShort()) // 주입량 4
        buffer.putShort(amount5.toShort()) // 주입량 5
        buffer.putShort(amount6.toShort()) // 주입량 6

        return suffixEncode(buffer)
    }

    override val friendlyName = "PUMP_BASAL_SETTING"
}