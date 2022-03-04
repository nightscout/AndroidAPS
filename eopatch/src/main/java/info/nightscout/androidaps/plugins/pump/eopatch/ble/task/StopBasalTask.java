package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import android.os.SystemClock;

import info.nightscout.androidaps.interfaces.PumpSync;
import info.nightscout.androidaps.logging.UserEntryLogger;
import info.nightscout.androidaps.utils.userEntry.UserEntryMapper;
import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BasalStop;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BasalStopResponse;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.interfaces.CommandQueue;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.commands.Command;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;

@Singleton
public class StopBasalTask extends TaskBase {
    @Inject IPreferenceManager pm;
    @Inject CommandQueue commandQueue;
    @Inject AAPSLogger aapsLogger;
    @Inject PumpSync pumpSync;
    @Inject UserEntryLogger uel;

    private final BasalStop BASAL_STOP;
    private final BehaviorSubject<Boolean> bolusCheckSubject = BehaviorSubject.create();
    private final BehaviorSubject<Boolean> exbolusCheckSubject = BehaviorSubject.create();
    private final BehaviorSubject<Boolean> basalCheckSubject = BehaviorSubject.create();

    @Inject
    public StopBasalTask() {
        super(TaskFunc.STOP_BASAL);

        BASAL_STOP = new BasalStop();
    }

    private Observable<Boolean> getBolusSebject(){
        return bolusCheckSubject.hide();
    }

    private Observable<Boolean> getExbolusSebject(){
        return exbolusCheckSubject.hide();
    }

    private Observable<Boolean> getBasalSebject(){
        return basalCheckSubject.hide();
    }

    public Single<BasalStopResponse> stop() {

        if (commandQueue.isRunning(Command.CommandType.BOLUS)) {
            uel.log(UserEntryMapper.Action.CANCEL_BOLUS, UserEntryMapper.Sources.EOPatch2);
            commandQueue.cancelAllBoluses();
            SystemClock.sleep(650);
        }
        bolusCheckSubject.onNext(true);

        if (pumpSync.expectedPumpState().getExtendedBolus() != null) {
            uel.log(UserEntryMapper.Action.CANCEL_EXTENDED_BOLUS, UserEntryMapper.Sources.EOPatch2);
            commandQueue.cancelExtended(new Callback() {
                @Override
                public void run() {
                    exbolusCheckSubject.onNext(true);
                }
            });
        }else{
            exbolusCheckSubject.onNext(true);
        }

        if (pumpSync.expectedPumpState().getTemporaryBasal() != null) {
            uel.log(UserEntryMapper.Action.CANCEL_TEMP_BASAL, UserEntryMapper.Sources.EOPatch2);
            commandQueue.cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    basalCheckSubject.onNext(true);
                }
            });
        }else{
            basalCheckSubject.onNext(true);
        }

        return Observable.zip(getBolusSebject(), getExbolusSebject(), getBasalSebject(), (bolusReady, exbolusReady, basalReady)
                    -> (bolusReady && exbolusReady && basalReady))
                .filter(ready -> ready)
                .flatMap(v -> isReady())
				.concatMapSingle(v -> BASAL_STOP.stop())
                .doOnNext(this::checkResponse)
                .firstOrError()
                .doOnSuccess(this::onBasalStopped)
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "StopBasalTask error"));
    }

    private void onBasalStopped(BasalStopResponse response) {
        enqueue(TaskFunc.UPDATE_CONNECTION);
    }

    public synchronized void enqueue() {
        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = stop()
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe(v -> {
                    bolusCheckSubject.onNext(false);
                    exbolusCheckSubject.onNext(false);
                    basalCheckSubject.onNext(false);
                });
        }
    }

    @Override
    protected void preCondition() throws Exception {
        checkPatchConnected();
    }
}
