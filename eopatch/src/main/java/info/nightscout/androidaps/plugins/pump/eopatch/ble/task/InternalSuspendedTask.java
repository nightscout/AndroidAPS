package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import android.os.SystemClock;

import info.nightscout.androidaps.interfaces.PumpSync;
import info.nightscout.androidaps.logging.UserEntryLogger;
import info.nightscout.androidaps.utils.userEntry.UserEntryMapper;
import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.core.define.IPatchConstant;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.interfaces.CommandQueue;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BolusStop;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetInternalSuspendTime;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.TempBasalScheduleStop;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchInternalSuspendTimeResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.commands.Command;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;

@Singleton
public class InternalSuspendedTask extends BolusTask {

    @Inject CommandQueue commandQueue;
    @Inject AAPSLogger aapsLogger;
    @Inject PumpSync pumpSync;
    @Inject UserEntryLogger uel;
	
    private GetInternalSuspendTime INTERNAL_SUSPEND_TIME_GET;
    private BolusStop BOLUS_STOP;
    private TempBasalScheduleStop TEMP_BASAL_SCHEDULE_STOP;
    private BehaviorSubject<Boolean> bolusCheckSubject = BehaviorSubject.create();
    private BehaviorSubject<Boolean> exbolusCheckSubject = BehaviorSubject.create();
    private BehaviorSubject<Boolean> basalCheckSubject = BehaviorSubject.create();

    @Inject
    public InternalSuspendedTask() {
        super(TaskFunc.INTERNAL_SUSPEND);

        INTERNAL_SUSPEND_TIME_GET = new GetInternalSuspendTime();
        BOLUS_STOP = new BolusStop();
        TEMP_BASAL_SCHEDULE_STOP = new TempBasalScheduleStop();
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

    public Single<Long> start(boolean isNowBolusActive, boolean isExtBolusActive, boolean isTempBasalActive) {
        PatchState patchState = pm.getPatchState();

        if (isNowBolusActive || isExtBolusActive) {
            enqueue(TaskFunc.READ_BOLUS_FINISH_TIME);
        }

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

        return Observable.zip(getBolusSebject(), getExbolusSebject(), getBasalSebject(), (bolusReady, exbolusReady, basalReady) -> {
                    return (bolusReady && exbolusReady && basalReady);
                })
                .filter(ready -> ready)
                .flatMap(v -> isReady())
                .concatMapSingle(v -> getInternalSuspendTime())
                .firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, e.getMessage()));
    }

    private Single<Long> getInternalSuspendTime() {
        return INTERNAL_SUSPEND_TIME_GET.get()
                .doOnSuccess(this::checkResponse)
                .map(PatchInternalSuspendTimeResponse::getTotalSeconds);
    }

    private Single<Long> stopNowBolus(long suspendTime, boolean isNowBolusActive) {
        if (isNowBolusActive) {
            long suspendedTimestamp = pm.getPatchConfig().getPatchWakeupTimestamp() + suspendTime;

            return BOLUS_STOP.stop(IPatchConstant.NOW_BOLUS_ID)
                       .doOnSuccess(this::checkResponse)
                       .doOnSuccess(v -> onNowBolusStopped(v.getInjectedBolusAmount(), suspendedTimestamp))
                       .map(v -> suspendTime);
        }

        return Single.just(suspendTime);
    }

    private Single<Long> stopExtBolus(long suspendTime, boolean isExtBolusActive) {
        if (isExtBolusActive) {
            long suspendedTimestamp = pm.getPatchConfig().getPatchWakeupTimestamp() + suspendTime;

            return BOLUS_STOP.stop(IPatchConstant.EXT_BOLUS_ID)
                       .doOnSuccess(this::checkResponse)
                       .doOnSuccess(v -> onExtBolusStopped(v.getInjectedBolusAmount(), suspendedTimestamp))
                       .map(v -> suspendTime);
        }

        return Single.just(suspendTime);
    }

    private Single<Long> stopTempBasal(long suspendTime, boolean isTempBasalActive) {
        if (isTempBasalActive) {
            return TEMP_BASAL_SCHEDULE_STOP.stop()
                       .doOnSuccess(this::checkResponse)
                       .doOnSuccess(v -> onTempBasalCanceled())
                       .map(v -> suspendTime);
        }

        return Single.just(suspendTime);
    }

    private void onNowBolusStopped(int injectedBolusAmount, long suspendedTimestamp) {
        updateNowBolusStopped(injectedBolusAmount, suspendedTimestamp);
    }

    private void onExtBolusStopped(int injectedBolusAmount, long suspendedTimestamp) {
        updateExtBolusStopped(injectedBolusAmount, suspendedTimestamp);
    }

    private void onTempBasalCanceled() {
        pm.getTempBasalManager().updateBasalStopped();
        pm.flushTempBasalManager();
    }

    public synchronized void enqueue(boolean isNowBolusActive, boolean isExtBolusActive, boolean isTempBasalActive) {
        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = start(isNowBolusActive, isExtBolusActive, isTempBasalActive)
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe(v -> {
                    bolusCheckSubject.onNext(false);
                    exbolusCheckSubject.onNext(false);
                    basalCheckSubject.onNext(false);
                });
        }
    }
}
