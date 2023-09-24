package info.nightscout.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * TempBasalSettingPacket
 */
class TempBasalSettingPacket(
    injector: HasAndroidInjector,
    private var status: Int, // (1:tempbasal running, 2:tempbasal dismissed)
    private var time: Int,  //hour group (1=00~05, 2=06~11, 3=12~17, 4=18~23)
    private var injectRateRatio: Int
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x0A
        aapsLogger.debug(LTag.PUMPCOMM, "TempBasalSettingPacket Init")
    }

    override fun encode(msgSeq: Int): ByteArray {
        val buffer = prefixEncode(msgType, msgSeq, MSG_CON_END)
        val apsSecond = 946652400L //fixed value 2000-01-01 00:00:00 (second)
        buffer.put(status.toByte()) // status
        buffer.put(time.toByte())
        buffer.putShort(injectRateRatio.toShort()) // 임시기저 주입량/률  1000(0.00U)~1600(6.00U), 50000(0%)~50200(200%), 50000이상이면 주입률로 판정
        buffer.putLong(apsSecond) // TB_DTTM

        aapsLogger.debug(LTag.PUMPCOMM, "         status --> ${status.toByte()} (1:tempbasal running, 2:tempbasal dismissed)")
        aapsLogger.debug(LTag.PUMPCOMM, "           time --> ${time.toByte()} ( value : 2~96 ( 2:30min, 3:45min....96:1440min )  30min ~ 24 hour, step 15min ) ")
        aapsLogger.debug(LTag.PUMPCOMM, "injectRateRatio --> $injectRateRatio , toShort = ${injectRateRatio.toShort()}")
        aapsLogger.debug(LTag.PUMPCOMM, "         tbDttm --> $apsSecond ")

        return suffixEncode(buffer)
    }

    override fun getFriendlyName(): String {
        return "PUMP_TEMP_BASAL_SETTING"
    }
}