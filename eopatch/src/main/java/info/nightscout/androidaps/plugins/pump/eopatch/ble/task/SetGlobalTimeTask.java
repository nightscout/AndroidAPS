package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetGlobalTime;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.SetGlobalTime;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.GlobalTimeResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchBooleanResponse;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Single;

@Singleton
public class SetGlobalTimeTask extends TaskBase {

    private SetGlobalTime SET_GLOBAL_TIME;
    private GetGlobalTime GET_GLOBAL_TIME;

    @Inject
    public SetGlobalTimeTask() {
        super(TaskFunc.SET_GLOBAL_TIME);

        SET_GLOBAL_TIME = new SetGlobalTime();
        GET_GLOBAL_TIME = new GetGlobalTime();
    }

    public Single<PatchBooleanResponse> set() {
        return isReady()
                .concatMapSingle(v -> GET_GLOBAL_TIME.get(false))
                .doOnNext(this::checkResponse)
                .doOnNext(this::checkPatchTime)
                .concatMapSingle(v -> SET_GLOBAL_TIME.set())
                .doOnNext(this::checkResponse)
                .firstOrError()
                .doOnSuccess(v -> onSuccess())
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, e.getMessage()));
    }

    private boolean checkPatchTime(GlobalTimeResponse response) throws Exception {

        long newMilli = System.currentTimeMillis();
        long oldMilli = response.getGlobalTimeInMilli();
        long oldOffset = response.getTimeZoneOffset();
        int offset = TimeZone.getDefault().getOffset(newMilli);
        int minutes = (int) TimeUnit.MILLISECONDS.toMinutes(offset);
        // TimeZoneOffset (8bit / signed): 타임존 offset 15분 단위을 1로 환산, Korea 의 경우 36값(+9:00)
        int newOffset = minutes / 15;

        long diff = Math.abs(oldMilli - newMilli);

        if (diff > 60000 || oldOffset != newOffset) {
            aapsLogger.debug(LTag.PUMPCOMM, String.format("checkPatchTime %s %s %s", diff, oldOffset, newOffset));
            return true;
        }

        throw new Exception("No time set required");
    }

    public synchronized void enqueue() {
        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = set()
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe(v -> {}, e -> {}); // Exception 을 사용하기에...
        }
    }

    private void onSuccess() {
    }
}
