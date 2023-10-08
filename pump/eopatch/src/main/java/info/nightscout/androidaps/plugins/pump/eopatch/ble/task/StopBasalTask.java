package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.data.ue.Action;
import app.aaps.core.data.ue.Sources;
import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.logging.UserEntryLogger;
import app.aaps.core.interfaces.pump.PumpSync;
import app.aaps.core.interfaces.queue.Callback;
import app.aaps.core.interfaces.queue.Command;
import app.aaps.core.interfaces.queue.CommandQueue;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BasalStop;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BasalStopResponse;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

@Singleton
public class StopBasalTask extends TaskBase {
    @Inject IPreferenceManager pm;
    @Inject CommandQueue commandQueue;
    @Inject AAPSLogger aapsLogger;
    @Inject PumpSync pumpSync;
    @Inject UserEntryLogger uel;

    @Inject UpdateConnectionTask updateConnectionTask;

    private final BasalStop BASAL_STOP;
    private final BehaviorSubject<Boolean> bolusCheckSubject = BehaviorSubject.create();
    private final BehaviorSubject<Boolean> extBolusCheckSubject = BehaviorSubject.create();
    private final BehaviorSubject<Boolean> basalCheckSubject = BehaviorSubject.create();

    @Inject
    public StopBasalTask() {
        super(TaskFunc.STOP_BASAL);

        BASAL_STOP = new BasalStop();
    }

    private Observable<Boolean> getBolusSubject() {
        return bolusCheckSubject.hide();
    }

    private Observable<Boolean> getExtBolusSubject() {
        return extBolusCheckSubject.hide();
    }

    private Observable<Boolean> getBasalSubject() {
        return basalCheckSubject.hide();
    }

    public Single<BasalStopResponse> stop() {

        if (commandQueue.isRunning(Command.CommandType.BOLUS)) {
            uel.log(Action.CANCEL_BOLUS, Sources.EOPatch2, "", new ArrayList<>());
            commandQueue.cancelAllBoluses(null);
            SystemClock.sleep(650);
        }
        bolusCheckSubject.onNext(true);

        if (pumpSync.expectedPumpState().getExtendedBolus() != null) {
            uel.log(Action.CANCEL_EXTENDED_BOLUS, Sources.EOPatch2, "", new ArrayList<>());
            commandQueue.cancelExtended(new Callback() {
                @Override
                public void run() {
                    extBolusCheckSubject.onNext(true);
                }
            });
        } else {
            extBolusCheckSubject.onNext(true);
        }

        if (pumpSync.expectedPumpState().getTemporaryBasal() != null) {
            uel.log(Action.CANCEL_TEMP_BASAL, Sources.EOPatch2, "", new ArrayList<>());
            commandQueue.cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    basalCheckSubject.onNext(true);
                }
            });
        } else {
            basalCheckSubject.onNext(true);
        }

        return Observable.zip(getBolusSubject(), getExtBolusSubject(), getBasalSubject(), (bolusReady, extBolusReady, basalReady)
                        -> (bolusReady && extBolusReady && basalReady))
                .filter(ready -> ready)
                .flatMap(v -> isReady())
                .concatMapSingle(v -> BASAL_STOP.stop())
                .doOnNext(this::checkResponse)
                .doOnNext(v -> updateConnectionTask.enqueue())
                .firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "StopBasalTask error"));
    }

    public synchronized void enqueue() {
        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = stop()
                    .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                    .subscribe(v -> {
                        bolusCheckSubject.onNext(false);
                        extBolusCheckSubject.onNext(false);
                        basalCheckSubject.onNext(false);
                    });
        }
    }

    @Override
    protected void preCondition() throws Exception {
        checkPatchConnected();
    }
}
