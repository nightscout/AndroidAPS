package app.aaps.pump.eopatch.ble.task

import app.aaps.pump.eopatch.AppConstant
import app.aaps.pump.eopatch.code.BolusExDuration
import app.aaps.pump.eopatch.core.code.BolusType
import app.aaps.pump.eopatch.core.util.FloatAdjusters

abstract class BolusTask(func: TaskFunc) : TaskBase(func) {

    fun onQuickBolusStarted(nowDoseU: Float, exDoseU: Float, exDuration: BolusExDuration) {
        val now = (nowDoseU > 0)
        val ext = (exDoseU > 0)

        val startTimestamp = if (now) System.currentTimeMillis() else 0
        val endTimestamp = startTimestamp + getPumpDuration(nowDoseU)

        val nowHistoryID = 1L //record no
        var exStartTimestamp: Long

        if (now) {
            pm.bolusCurrent.startNowBolus(nowHistoryID, nowDoseU, startTimestamp, endTimestamp)
        }
        if (ext) {
            var estimatedExStartTimestamp: Long

            if (now) {
                exStartTimestamp = 0
            } else {
                estimatedExStartTimestamp = System.currentTimeMillis()
                exStartTimestamp = estimatedExStartTimestamp
            }
            val exEndTimestamp = exStartTimestamp + exDuration.milli()

            val extHistoryID = 2L //record no
            pm.bolusCurrent.startExtBolus(
                extHistoryID, exDoseU, exStartTimestamp,
                exEndTimestamp, exDuration.milli()
            )
        }

        pm.flushBolusCurrent()
    }

    fun onCalcBolusStarted(nowDoseU: Float) {
        val now = (nowDoseU > 0)

        val startTimestamp = if (now) System.currentTimeMillis() else 0 // dm_1720
        val endTimestamp = startTimestamp + getPumpDuration(nowDoseU)

        val nowHistoryID = 1L //record no

        if (now) {
            pm.bolusCurrent.startNowBolus(nowHistoryID, nowDoseU, startTimestamp, endTimestamp)
        }

        pm.flushBolusCurrent()
    }

    fun updateNowBolusStopped(injected: Int, suspendedTimestamp: Long = 0) {
        val bolusCurrent = pm.bolusCurrent
        val nowID = bolusCurrent.historyId(BolusType.NOW)
        if (nowID > 0 && !bolusCurrent.endTimeSynced(BolusType.NOW)) {
            val stopTime = if ((suspendedTimestamp > 0)) suspendedTimestamp else System.currentTimeMillis()
            val injectedDoseU = FloatAdjusters.FLOOR2_BOLUS.apply(injected * AppConstant.INSULIN_UNIT_P)
            bolusCurrent.nowBolus.injected = injectedDoseU
            bolusCurrent.nowBolus.endTimestamp = stopTime
            bolusCurrent.setEndTimeSynced(BolusType.NOW, true)
            pm.flushBolusCurrent()
        }
    }

    fun updateExtBolusStopped(injected: Int, suspendedTimestamp: Long = 0) {
        val bolusCurrent = pm.bolusCurrent
        val extID = bolusCurrent.historyId(BolusType.EXT)
        if (extID > 0 && !bolusCurrent.endTimeSynced(BolusType.EXT)) {
            val stopTime = if ((suspendedTimestamp > 0)) suspendedTimestamp else System.currentTimeMillis()
            val injectedDoseU = FloatAdjusters.FLOOR2_BOLUS.apply(injected * AppConstant.INSULIN_UNIT_P)
            bolusCurrent.extBolus.injected = injectedDoseU
            bolusCurrent.extBolus.endTimestamp = stopTime
            bolusCurrent.setEndTimeSynced(BolusType.EXT, true)
            pm.flushBolusCurrent()
        }
    }

    private fun getPumpDuration(doseU: Float): Long {
        if (doseU > 0) {
            val pumpDuration = patchConfig.pumpDurationSmallMilli
            return ((doseU / AppConstant.BOLUS_UNIT_STEP) * pumpDuration).toLong()
        }
        return 0L
    }
}
