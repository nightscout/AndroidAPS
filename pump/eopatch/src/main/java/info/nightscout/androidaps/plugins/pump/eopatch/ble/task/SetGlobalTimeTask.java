package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetGlobalTime;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.SetGlobalTime;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.GlobalTimeResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchBooleanResponse;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class SetGlobalTimeTask extends TaskBase {
    private final SetGlobalTime SET_GLOBAL_TIME;
    private final GetGlobalTime GET_GLOBAL_TIME;

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
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "SetGlobalTimeTask error"));
    }

    private void checkPatchTime(GlobalTimeResponse response) throws Exception {

        long newMilli = System.currentTimeMillis();
        long oldMilli = response.getGlobalTimeInMilli();
        long oldOffset = response.getTimeZoneOffset();
        int offset = TimeZone.getDefault().getOffset(newMilli);
        int minutes = (int) TimeUnit.MILLISECONDS.toMinutes(offset);
        int newOffset = minutes / 15;

        long diff = Math.abs(oldMilli - newMilli);

        if (diff > 60000 || oldOffset != newOffset) {
            aapsLogger.debug(LTag.PUMPCOMM, String.format("checkPatchTime %s %s %s", diff, oldOffset, newOffset));
            return;
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
