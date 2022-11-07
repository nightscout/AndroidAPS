package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;


import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.core.api.StartPriming;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.UpdateConnection;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Observable;

@Singleton
public class PrimingTask extends TaskBase {
    private final UpdateConnection UPDATE_CONNECTION;
    private final StartPriming START_PRIMING;

    @Inject
    public PrimingTask() {
        super(TaskFunc.PRIMING);

        UPDATE_CONNECTION = new UpdateConnection();
        START_PRIMING = new StartPriming();
    }

    public Observable<Long> start(long count) {
        return isReady().concatMapSingle(v -> START_PRIMING.start())
                .doOnNext(this::checkResponse)
                .flatMap(v -> observePrimingSuccess(count))
                .takeUntil(value -> (value == count))
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "PrimingTask error"));
    }

    private Observable<Long> observePrimingSuccess(long count) {

        return Observable.merge(
                Observable.interval(1, TimeUnit.SECONDS).take(count + 10)
                        .map(v -> v * 3)
                        .doOnNext(v -> {
                            if (v >= count) {
                                throw new Exception("Priming failed");
                            }
                        }),

                Observable.interval(3, TimeUnit.SECONDS)
                        .concatMapSingle(v -> UPDATE_CONNECTION.get())
                        .map(response -> PatchState.Companion.create(response.getPatchState(), System.currentTimeMillis()))
                        .filter(PatchState::isPrimingSuccess)
                        .map(result -> count)
        );
    }
}
