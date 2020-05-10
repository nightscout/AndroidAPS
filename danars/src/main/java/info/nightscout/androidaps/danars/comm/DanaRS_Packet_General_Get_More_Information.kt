package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaRPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import org.joda.time.DateTime
import javax.inject.Inject

class DanaRS_Packet_General_Get_More_Information(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaRPump: DanaRPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_MORE_INFORMATION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 15) {
            failed = true
            return
        }
        danaRPump.iob = intFromBuff(data, 0, 2) / 100.0
        danaRPump.dailyTotalUnits = intFromBuff(data, 2, 2) / 100.0
        danaRPump.isExtendedInProgress = intFromBuff(data, 4, 1) == 0x01
        danaRPump.extendedBolusRemainingMinutes = intFromBuff(data, 5, 2)
        // val remainRate = intFromBuff(data, 7, 2) / 100.0
        val hours = intFromBuff(data, 9, 1)
        val minutes = intFromBuff(data, 10, 1)
        danaRPump.lastBolusTime = DateTime.now().withHourOfDay(hours).withMinuteOfHour(minutes).millis
        danaRPump.lastBolusAmount = intFromBuff(data, 11, 2) / 100.0
        // On DanaRS DailyUnits can't be more than 160
        if (danaRPump.dailyTotalUnits > 160) failed = true
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total units: " + danaRPump.dailyTotalUnits.toString() + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Is extended in progress: " + danaRPump.isExtendedInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus remaining minutes: " + danaRPump.extendedBolusRemainingMinutes)
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus time: " + dateUtil.dateAndTimeAndSecondsString(danaRPump.lastBolusTime))
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus amount: " + danaRPump.lastBolusAmount)
    }

    override fun getFriendlyName(): String {
        return "REVIEW__GET_MORE_INFORMATION"
    }
}