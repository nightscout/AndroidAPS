package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.logging.LTag
import kotlin.math.ceil

class MsgStatusTempBasal(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        SetCommand(0x0205)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val isTempBasalInProgress = intFromBuff(bytes, 0, 1) and 0x01 == 0x01
        val isAPSTempBasalInProgress = intFromBuff(bytes, 0, 1) and 0x02 == 0x02
        var tempBasalPercent = intFromBuff(bytes, 1, 1)
        if (tempBasalPercent > 200) tempBasalPercent = (tempBasalPercent - 200) * 10
        val tempBasalTotalSec: Int = if (intFromBuff(bytes, 2, 1) == 150) 15 * 60 else if (intFromBuff(bytes, 2, 1) == 160) 30 * 60 else intFromBuff(bytes, 2, 1) * 60 * 60
        val tempBasalRunningSeconds = intFromBuff(bytes, 3, 3)
        val tempBasalRemainingMin = (tempBasalTotalSec - tempBasalRunningSeconds) / 60
        val tempBasalStart = if (isTempBasalInProgress) getDateFromTempBasalSecAgo(tempBasalRunningSeconds) else 0
        danaPump.isTempBasalInProgress = isTempBasalInProgress
        danaPump.tempBasalPercent = tempBasalPercent
        danaPump.tempBasalRemainingMin = tempBasalRemainingMin
        danaPump.tempBasalTotalSec = tempBasalTotalSec
        danaPump.tempBasalStart = tempBasalStart
        updateTempBasalInDB()
        aapsLogger.debug(LTag.PUMPCOMM, "Is temp basal running: $isTempBasalInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "Is APS temp basal running: $isAPSTempBasalInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal percent: $tempBasalPercent")
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal remaining min: $tempBasalRemainingMin")
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal total sec: $tempBasalTotalSec")
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal start: $tempBasalStart")
    }

    private fun getDateFromTempBasalSecAgo(tempBasalAgoSecs: Int): Long {
        return (ceil(System.currentTimeMillis() / 1000.0) - tempBasalAgoSecs).toLong() * 1000
    }

    private fun updateTempBasalInDB() {
        val now = System.currentTimeMillis()
        if (activePlugin.activeTreatments.isInHistoryRealTempBasalInProgress) {
            val tempBasal = activePlugin.activeTreatments.getRealTempBasalFromHistory(System.currentTimeMillis())
            if (danaPump.isTempBasalInProgress) {
                if (tempBasal.percentRate != danaPump.tempBasalPercent) { // Close current temp basal
                    val tempStop = TemporaryBasal(injector).date(danaPump.tempBasalStart - 1000).source(Source.USER)
                    activePlugin.activeTreatments.addToHistoryTempBasal(tempStop)
                    // Create new
                    val newTempBasal = TemporaryBasal(injector)
                        .date(danaPump.tempBasalStart)
                        .percent(danaPump.tempBasalPercent)
                        .duration(danaPump.tempBasalTotalSec / 60)
                        .source(Source.USER)
                    activePlugin.activeTreatments.addToHistoryTempBasal(newTempBasal)
                }
            } else { // Close current temp basal
                val tempStop = TemporaryBasal(injector).date(now).source(Source.USER)
                activePlugin.activeTreatments.addToHistoryTempBasal(tempStop)
            }
        } else {
            if (danaPump.isTempBasalInProgress) { // Create new
                val newTempBasal = TemporaryBasal(injector)
                    .date(danaPump.tempBasalStart)
                    .percent(danaPump.tempBasalPercent)
                    .duration(danaPump.tempBasalTotalSec / 60)
                    .source(Source.USER)
                activePlugin.activeTreatments.addToHistoryTempBasal(newTempBasal)
            }
        }
    }
}