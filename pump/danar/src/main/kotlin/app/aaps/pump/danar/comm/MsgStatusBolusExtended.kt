package app.aaps.pump.danar.comm

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import kotlin.math.abs
import kotlin.math.floor

class MsgStatusBolusExtended(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0207)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val isExtendedInProgress = intFromBuff(bytes, 0, 1) == 1
        val extendedBolusHalfHours = intFromBuff(bytes, 1, 1)
        val extendedBolusMinutes = extendedBolusHalfHours * 30L
        val extendedBolusAmount = intFromBuff(bytes, 2, 2) / 100.0
        val extendedBolusSoFarInSecs = intFromBuff(bytes, 4, 3)
        // This is available only on korean, but not needed now
//        int extendedBolusDeliveryPulse = intFromBuff(bytes, 7, 2);
//        int isEasyUIUserSleep = intFromBuff(bytes, 9, 1);
        val extendedBolusSoFarInMinutes = extendedBolusSoFarInSecs / 60
        val extendedBolusAbsoluteRate = if (isExtendedInProgress) extendedBolusAmount / extendedBolusMinutes * 60 else 0.0
        val extendedBolusStart = if (isExtendedInProgress) getDateFromSecAgo(extendedBolusSoFarInSecs) else 0
        val extendedBolusRemainingMinutes = extendedBolusMinutes - extendedBolusSoFarInMinutes
        if (isExtendedInProgress && !isWithin3Sec(extendedBolusStart)) {
            danaPump.extendedBolusDuration = T.mins(extendedBolusMinutes).msecs()
            danaPump.extendedBolusAmount = extendedBolusAmount
            danaPump.extendedBolusStart = extendedBolusStart
            aapsLogger.debug(LTag.PUMPCOMM, "New extended bolus detected")
        } else if (!isExtendedInProgress) {
            aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus stopped. Previous state: ${danaPump.isExtendedInProgress}")
            danaPump.isExtendedInProgress = false
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "No change in extended bolus. Current state: ${danaPump.isExtendedInProgress}")
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Is extended bolus running: $isExtendedInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus min: $extendedBolusMinutes")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus amount: $extendedBolusAmount")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus so far in minutes: $extendedBolusSoFarInMinutes")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus absolute rate: $extendedBolusAbsoluteRate")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus start: " + dateUtil.dateAndTimeString(extendedBolusStart))
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus remaining minutes: $extendedBolusRemainingMinutes")
    }

    private fun getDateFromSecAgo(tempBasalAgoSecs: Int): Long {
        return (floor(System.currentTimeMillis() / 1000.0) - tempBasalAgoSecs).toLong() * 1000
    }

    // because there is no fixed timestamp of start allow update of eb only if tbr start differs more
    private fun isWithin3Sec(newStart: Long) = abs(newStart - danaPump.extendedBolusStart) < 3000
}