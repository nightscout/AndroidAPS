package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.shared.utils.T
import info.nightscout.rx.logging.LTag
import kotlin.math.abs
import kotlin.math.floor

class MsgStatusTempBasal(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0205)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    var isTempBasalInProgress = false

    override fun handleMessage(bytes: ByteArray) {
        isTempBasalInProgress = intFromBuff(bytes, 0, 1) and 0x01 == 0x01
        val isAPSTempBasalInProgress = intFromBuff(bytes, 0, 1) and 0x02 == 0x02
        var tempBasalPercent = intFromBuff(bytes, 1, 1)
        if (tempBasalPercent > 200) tempBasalPercent = (tempBasalPercent - 200) * 10
        var tempBasalTotalMin = intFromBuff(bytes, 2, 1) * 60
        tempBasalTotalMin = if (tempBasalTotalMin == 150) 15 else if (tempBasalTotalMin == 160) 30 else tempBasalTotalMin
        val tempBasalTotalSec = tempBasalTotalMin * 60
        val tempBasalRunningSeconds = intFromBuff(bytes, 3, 3)
        val tempBasalRemainingMin = (tempBasalTotalSec - tempBasalRunningSeconds) / 60
        val tempBasalStart = if (isTempBasalInProgress) getDateFromSecAgo(tempBasalRunningSeconds) else 0
        if (isTempBasalInProgress && !isWithin3Sec(tempBasalStart)) {
            danaPump.tempBasalStart = tempBasalStart
            danaPump.tempBasalPercent = tempBasalPercent
            danaPump.tempBasalDuration = T.mins(tempBasalTotalMin.toLong()).msecs()
            aapsLogger.debug(LTag.PUMPCOMM, "New temp basal detected")
        } else if (!isTempBasalInProgress) {
            aapsLogger.debug(LTag.PUMPCOMM, "Temp basal stopped. Previous state: ${danaPump.isTempBasalInProgress}")
            danaPump.isTempBasalInProgress = false
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "No change in temp basal. Current state: ${danaPump.isTempBasalInProgress}")
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Is temp basal running: $isTempBasalInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "Is APS temp basal running: $isAPSTempBasalInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal percent: $tempBasalPercent")
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal remaining min: $tempBasalRemainingMin")
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal total min: $tempBasalTotalMin")
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal start: " + dateUtil.dateAndTimeString(tempBasalStart))
    }

    private fun getDateFromSecAgo(tempBasalAgoSecs: Int): Long {
        return (floor(dateUtil.now() / 1000.0) - tempBasalAgoSecs).toLong() * 1000
    }

    // because there is no fixed timestamp of start allow update of tbr only if tbr start differs more
    private fun isWithin3Sec(newStart: Long) = abs(newStart - danaPump.tempBasalStart) < 3000
}