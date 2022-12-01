package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BasalHistoryGetExBig;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BasalHistoryIndexGet;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.TempBasalHistoryGetExBig;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BasalHistoryIndexResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BasalHistoryResponse;
import io.reactivex.rxjava3.core.Single;

import static info.nightscout.androidaps.plugins.pump.eopatch.core.define.IPatchConstant.BASAL_HISTORY_SIZE_BIG;

@Singleton
public class SyncBasalHistoryTask extends TaskBase {
    @Inject IPreferenceManager pm;

    private final BasalHistoryIndexGet BASAL_HISTORY_INDEX_GET;
    private final BasalHistoryGetExBig BASAL_HISTORY_GET_EX_BIG;
    private final TempBasalHistoryGetExBig TEMP_BASAL_HISTORY_GET_EX_BIG;

    @Inject
    public SyncBasalHistoryTask() {
        super(TaskFunc.SYNC_BASAL_HISTORY);

        BASAL_HISTORY_INDEX_GET = new BasalHistoryIndexGet();
        BASAL_HISTORY_GET_EX_BIG = new BasalHistoryGetExBig();
        TEMP_BASAL_HISTORY_GET_EX_BIG = new TempBasalHistoryGetExBig();
    }

    public Single<Integer> sync(int end) {
        return Single.just(1);  // 베이젤 싱크 사용 안함
    }

    public Single<Integer> sync() {
        return Single.just(1);  // 베이젤 싱크 사용 안함
    }

    private Single<Integer> getLastIndex() {
        return BASAL_HISTORY_INDEX_GET.get()
                .doOnSuccess(this::checkResponse)
                .map(BasalHistoryIndexResponse::getLastFinishedIndex);
    }

    private Single<Integer> syncBoth(int start, int end) {
        int count = end - start + 1;

        if (count > 0) {
            return Single.zip(
                    BASAL_HISTORY_GET_EX_BIG.get(start, count),
                    TEMP_BASAL_HISTORY_GET_EX_BIG.get(start, count),
                    (normal, temp) -> onBasalHistoryResponse(normal, temp, start, end));
        } else {
            return Single.just(-1);
        }
    }

    public synchronized void enqueue(int end) {

        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = sync(end)
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe();
        }
    }

    public synchronized void enqueue() {
        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = sync()
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe();
        }
    }

    private int onBasalHistoryResponse(BasalHistoryResponse n, BasalHistoryResponse t,
                                       int startRequested, int end) {

        if (!n.isSuccess() || !t.isSuccess() || n.getSeq() != t.getSeq()) {
            return -1;
        }

        int start = n.getSeq();

        float[] normal = n.getInjectedDoseValues();
        float[] temp = t.getInjectedDoseValues();

        int count = Math.min(end - start + 1, BASAL_HISTORY_SIZE_BIG);
        count = Math.min(count, normal.length);
        count = Math.min(count, temp.length);

        return updateInjected(normal, temp, start, end);
    }

    public synchronized int updateInjected(float[] normal, float[] temp, int start, int end) {
        if (pm.getPatchState().isPatchInternalSuspended() && !pm.getPatchConfig().isInBasalPausedTime()) {
            return -1;
        }

        int lastUpdatedIndex = -1;
        int count = end - start + 1;

        if (count > normal.length) {
            count = normal.length;
        }

        if (count > 0) {
            int lastSyncIndex = pm.getPatchConfig().getLastIndex();
            for (int i = 0;i < count;i++) {
                int seq = start + i;
                if (seq < lastSyncIndex)
                    continue;

                if (start <= seq && seq <= end) {
                    lastUpdatedIndex = seq;
                }
            }
        }

        return lastUpdatedIndex;
    }

    private void updatePatchLastIndex(int newIndex) {
        int lastIndex = pm.getPatchConfig().getLastIndex();

        if (lastIndex < newIndex) {
            pm.getPatchConfig().setLastIndex(newIndex);
            pm.flushPatchConfig();
        }
    }
}