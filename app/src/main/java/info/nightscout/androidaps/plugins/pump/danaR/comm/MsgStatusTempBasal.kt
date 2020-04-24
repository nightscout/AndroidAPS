package info.nightscout.androidaps.plugins.pump.danaR.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import kotlin.math.ceil

class MsgStatusTempBasal(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump,
    private val activePlugin: ActivePluginProvider,
    private val injector: HasAndroidInjector
) : MessageBase() {

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
        danaRPump.isTempBasalInProgress = isTempBasalInProgress
        danaRPump.tempBasalPercent = tempBasalPercent
        danaRPump.tempBasalRemainingMin = tempBasalRemainingMin
        danaRPump.tempBasalTotalSec = tempBasalTotalSec
        danaRPump.tempBasalStart = tempBasalStart
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
            if (danaRPump.isTempBasalInProgress) {
                if (tempBasal.percentRate != danaRPump.tempBasalPercent) { // Close current temp basal
                    val tempStop = TemporaryBasal(injector).date(danaRPump.tempBasalStart - 1000).source(Source.USER)
                    activePlugin.activeTreatments.addToHistoryTempBasal(tempStop)
                    // Create new
                    val newTempBasal = TemporaryBasal(injector)
                        .date(danaRPump.tempBasalStart)
                        .percent(danaRPump.tempBasalPercent)
                        .duration(danaRPump.tempBasalTotalSec / 60)
                        .source(Source.USER)
                    activePlugin.activeTreatments.addToHistoryTempBasal(newTempBasal)
                }
            } else { // Close current temp basal
                val tempStop = TemporaryBasal(injector).date(now).source(Source.USER)
                activePlugin.activeTreatments.addToHistoryTempBasal(tempStop)
            }
        } else {
            if (danaRPump.isTempBasalInProgress) { // Create new
                val newTempBasal = TemporaryBasal(injector)
                    .date(danaRPump.tempBasalStart)
                    .percent(danaRPump.tempBasalPercent)
                    .duration(danaRPump.tempBasalTotalSec / 60)
                    .source(Source.USER)
                activePlugin.activeTreatments.addToHistoryTempBasal(newTempBasal)
            }
        }
    }
}