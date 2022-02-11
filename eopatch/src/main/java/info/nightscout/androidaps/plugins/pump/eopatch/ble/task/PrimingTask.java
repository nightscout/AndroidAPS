package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;


import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.StartPriming;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.UpdateConnection;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import io.reactivex.Observable;

@Singleton
public class PrimingTask extends TaskBase {

    private UpdateConnection UPDATE_CONNECTION;
    private StartPriming START_PRIMING;

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
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, e.getMessage()));
    }

    private Observable<Long> observePrimingSuccess(long count) {

        return Observable.merge(
                Observable.interval(1, TimeUnit.SECONDS).take(count + 10)
                        .map(v -> v * 3) // 현재 20초 니깐 60 정도에서 꽉 채워짐. *4 도 괜찮을 듯.
                        .doOnNext(v -> {
                            if (v >= count) {
                                throw new Exception("Priming failed");
                            }
                        }), // 프로그래스바 용.

                Observable.interval(3, TimeUnit.SECONDS)
                        .concatMapSingle(v -> UPDATE_CONNECTION.get())
                        .map(response -> PatchState.Companion.create(response.getPatchState(), System.currentTimeMillis()))
                        .filter(patchState -> patchState.isPrimingSuccess())
                        .map(result -> count) // 프라이밍 체크 용 성공시 count 값 리턴
        );
    }
}
