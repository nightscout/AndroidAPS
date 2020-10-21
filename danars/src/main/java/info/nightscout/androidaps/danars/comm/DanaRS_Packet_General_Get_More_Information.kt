package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import javax.inject.Inject

class DanaRS_Packet_General_Get_More_Information(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_MORE_INFORMATION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 15) {
            failed = true
            return
        }
        danaPump.iob = intFromBuff(data, 0, 2) / 100.0
        danaPump.dailyTotalUnits = intFromBuff(data, 2, 2) / 100.0
        danaPump.isExtendedInProgress = intFromBuff(data, 4, 1) == 0x01
        danaPump.extendedBolusRemainingMinutes = intFromBuff(data, 5, 2)
        // val remainRate = intFromBuff(data, 7, 2) / 100.0
        val hours = intFromBuff(data, 9, 1)
        val minutes = intFromBuff(data, 10, 1)
        if (danaPump.usingUTC) danaPump.lastBolusTime = DateTime.now().withZone(DateTimeZone.UTC).withHourOfDay(hours).withMinuteOfHour(minutes).millis
        else danaPump.lastBolusTime = DateTime.now().withHourOfDay(hours).withMinuteOfHour(minutes).millis
        danaPump.lastBolusAmount = intFromBuff(data, 11, 2) / 100.0
        // On DanaRS DailyUnits can't be more than 160
        if (danaPump.dailyTotalUnits > 160) failed = true
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total units: " + danaPump.dailyTotalUnits.toString() + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Is extended in progress: " + danaPump.isExtendedInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus remaining minutes: " + danaPump.extendedBolusRemainingMinutes)
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus time: " + dateUtil.dateAndTimeAndSecondsString(danaPump.lastBolusTime))
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus amount: " + danaPump.lastBolusAmount)
    }

    override fun getFriendlyName(): String {
        return "REVIEW__GET_MORE_INFORMATION"
    }
}