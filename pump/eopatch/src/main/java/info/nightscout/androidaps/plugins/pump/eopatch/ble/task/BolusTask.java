package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import info.nightscout.androidaps.plugins.pump.eopatch.code.BolusExDuration;

import info.nightscout.androidaps.plugins.pump.eopatch.AppConstant;
import info.nightscout.androidaps.plugins.pump.eopatch.core.code.BolusType;
import info.nightscout.androidaps.plugins.pump.eopatch.core.util.FloatAdjusters;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.BolusCurrent;

abstract class BolusTask extends TaskBase {

    public BolusTask(TaskFunc func) {
        super(func);
    }

    public void onQuickBolusStarted(float nowDoseU, float exDoseU, BolusExDuration exDuration) {
        boolean now = (nowDoseU > 0);
        boolean ext = (exDoseU > 0);

        long startTimestamp = now ? System.currentTimeMillis() : 0;
        long endTimestamp = startTimestamp + getPumpDuration(nowDoseU);

        long nowHistoryID = 1L;  //record no
        long exStartTimestamp;

        if (now) {
            pm.getBolusCurrent().startNowBolus(nowHistoryID, nowDoseU, startTimestamp, endTimestamp);
        }
        if (ext) {
            long estimatedExStartTimestamp;

            if (now) {
                exStartTimestamp = 0;
            }
            else {
                estimatedExStartTimestamp = System.currentTimeMillis();
                exStartTimestamp = estimatedExStartTimestamp;
            }
            long exEndTimestamp = exStartTimestamp + exDuration.milli();

            long extHistoryID = 2L;  //record no
            pm.getBolusCurrent().startExtBolus(extHistoryID, exDoseU, exStartTimestamp,
                    exEndTimestamp, exDuration.milli());
        }

        pm.flushBolusCurrent();
    }


    public void onCalcBolusStarted(float nowDoseU) {
        boolean now = (nowDoseU > 0);

        long startTimestamp = now ? System.currentTimeMillis() : 0;     // dm_1720
        long endTimestamp = startTimestamp + getPumpDuration(nowDoseU);

        long nowHistoryID = 1L;  //record no

        if (now) {
            pm.getBolusCurrent().startNowBolus(nowHistoryID, nowDoseU, startTimestamp, endTimestamp);
        }

        pm.flushBolusCurrent();
    }

    public void updateNowBolusStopped(int injected) {
        updateNowBolusStopped(injected, 0);
    }

    public void updateNowBolusStopped(int injected, long suspendedTimestamp) {
        BolusCurrent bolusCurrent = pm.getBolusCurrent();
        long nowID = bolusCurrent.historyId(BolusType.NOW);
        if (nowID > 0 && !bolusCurrent.endTimeSynced(BolusType.NOW)) {
            long stopTime = (suspendedTimestamp > 0) ? suspendedTimestamp : System.currentTimeMillis();
            float injectedDoseU = FloatAdjusters.FLOOR2_BOLUS.apply(injected * AppConstant.INSULIN_UNIT_P);
            bolusCurrent.getNowBolus().setInjected(injectedDoseU);
            bolusCurrent.getNowBolus().setEndTimestamp(stopTime);
            bolusCurrent.setEndTimeSynced(BolusType.NOW, true);
            pm.flushBolusCurrent();
        }
    }

    public void updateExtBolusStopped(int injected) {
        updateExtBolusStopped(injected, 0);
    }

    public void updateExtBolusStopped(int injected, long suspendedTimestamp) {
        BolusCurrent bolusCurrent = pm.getBolusCurrent();
        long extID = bolusCurrent.historyId(BolusType.EXT);
        if (extID > 0 && !bolusCurrent.endTimeSynced(BolusType.EXT)) {
            long stopTime = (suspendedTimestamp > 0) ? suspendedTimestamp : System.currentTimeMillis();
            float injectedDoseU = FloatAdjusters.FLOOR2_BOLUS.apply(injected * AppConstant.INSULIN_UNIT_P);
            bolusCurrent.getExtBolus().setInjected(injectedDoseU);
            bolusCurrent.getExtBolus().setEndTimestamp(stopTime);
            bolusCurrent.setEndTimeSynced(BolusType.EXT, true);
            pm.flushBolusCurrent();
        }
    }

    private long getPumpDuration(float doseU) {
        if (doseU > 0) {
            long pumpDuration = pm.getPatchConfig().getPumpDurationSmallMilli();
            return (long) ((doseU / AppConstant.BOLUS_UNIT_STEP) * pumpDuration);
        }
        return 0L;
    }
}
