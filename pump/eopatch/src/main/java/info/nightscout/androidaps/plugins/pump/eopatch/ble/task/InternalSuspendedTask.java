package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.logging.UserEntryLogger;
import app.aaps.core.interfaces.pump.PumpSync;
import app.aaps.core.interfaces.queue.Callback;
import app.aaps.core.interfaces.queue.Command;
import app.aaps.core.interfaces.queue.CommandQueue;
import app.aaps.core.interfaces.userEntry.UserEntryMapper;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetInternalSuspendTime;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchInternalSuspendTimeResponse;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

@Singleton
public class InternalSuspendedTask extends BolusTask {
    @Inject CommandQueue commandQueue;
    @Inject AAPSLogger aapsLogger;
    @Inject PumpSync pumpSync;
    @Inject UserEntryLogger uel;

    private final GetInternalSuspendTime INTERNAL_SUSPEND_TIME_GET;
    private final BehaviorSubject<Boolean> bolusCheckSubject = BehaviorSubject.create();
    private final BehaviorSubject<Boolean> extendedBolusCheckSubject = BehaviorSubject.create();
    private final BehaviorSubject<Boolean> basalCheckSubject = BehaviorSubject.create();

    @Inject
    public InternalSuspendedTask() {
        super(TaskFunc.INTERNAL_SUSPEND);

        INTERNAL_SUSPEND_TIME_GET = new GetInternalSuspendTime();
    }

    private Observable<Boolean> getBolusSubject() {
        return bolusCheckSubject.hide();
    }

    private Observable<Boolean> getExtendedBolusSubject() {
        return extendedBolusCheckSubject.hide();
    }

    private Observable<Boolean> getBasalSubject() {
        return basalCheckSubject.hide();
    }

    public Single<Long> start(boolean isNowBolusActive, boolean isExtBolusActive, boolean isTempBasalActive) {
        if (isNowBolusActive || isExtBolusActive) {
            enqueue(TaskFunc.READ_BOLUS_FINISH_TIME);
        }

        if (isTempBasalActive) {
            enqueue(TaskFunc.READ_TEMP_BASAL_FINISH_TIME);
        }

        if (commandQueue.isRunning(Command.CommandType.BOLUS)) {
            uel.log(UserEntryMapper.Action.CANCEL_BOLUS, UserEntryMapper.Sources.EOPatch2);
            commandQueue.cancelAllBoluses(null);
            SystemClock.sleep(650);
        }
        bolusCheckSubject.onNext(true);

        if (pumpSync.expectedPumpState().getExtendedBolus() != null) {
            uel.log(UserEntryMapper.Action.CANCEL_EXTENDED_BOLUS, UserEntryMapper.Sources.EOPatch2);
            commandQueue.cancelExtended(new Callback() {
                @Override
                public void run() {
                    extendedBolusCheckSubject.onNext(true);
                }
            });
        } else {
            extendedBolusCheckSubject.onNext(true);
        }

        if (pumpSync.expectedPumpState().getTemporaryBasal() != null) {
            uel.log(UserEntryMapper.Action.CANCEL_TEMP_BASAL, UserEntryMapper.Sources.EOPatch2);
            commandQueue.cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    basalCheckSubject.onNext(true);
                }
            });
        } else {
            basalCheckSubject.onNext(true);
        }

        return Observable.zip(getBolusSubject(), getExtendedBolusSubject(), getBasalSubject(),
                        (bolusReady, extendedBolusReady, basalReady) -> (bolusReady && extendedBolusReady && basalReady))
                .filter(ready -> ready)
                .flatMap(v -> isReady())
                .concatMapSingle(v -> getInternalSuspendTime())
                .firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "InternalSuspendedTask error"));
    }

    private Single<Long> getInternalSuspendTime() {
        return INTERNAL_SUSPEND_TIME_GET.get()
                .doOnSuccess(this::checkResponse)
                .map(PatchInternalSuspendTimeResponse::getTotalSeconds);
    }

    public synchronized void enqueue(boolean isNowBolusActive, boolean isExtBolusActive, boolean isTempBasalActive) {
        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = start(isNowBolusActive, isExtBolusActive, isTempBasalActive)
                    .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                    .subscribe(v -> {
                        bolusCheckSubject.onNext(false);
                        extendedBolusCheckSubject.onNext(false);
                        basalCheckSubject.onNext(false);
                    });
        }
    }
}
