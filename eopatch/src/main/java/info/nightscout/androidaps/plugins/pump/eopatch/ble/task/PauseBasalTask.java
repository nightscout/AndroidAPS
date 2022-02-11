package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;


import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.interfaces.CommandQueue;
import info.nightscout.androidaps.interfaces.PumpSync;
import info.nightscout.androidaps.logging.UserEntryLogger;
import info.nightscout.androidaps.utils.userEntry.UserEntryMapper;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode;
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.IAlarmRegistry;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.define.IPatchConstant;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BasalPause;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BolusStop;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.TempBasalScheduleStop;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchBooleanResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasal;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.commands.Command;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;

@Singleton
public class PauseBasalTask extends BolusTask {
    @Inject IAlarmRegistry alarmRegistry;
    @Inject IPreferenceManager pm;
    @Inject CommandQueue commandQueue;
    @Inject AAPSLogger aapsLogger;
    @Inject PumpSync pumpSync;
    @Inject UserEntryLogger uel;

    private BasalPause BASAL_PAUSE;
    private BolusStop BOLUS_STOP;
    private TempBasalScheduleStop TEMP_BASAL_SCHEDULE_STOP;

    private BehaviorSubject<Boolean> bolusCheckSubject = BehaviorSubject.create();
    private BehaviorSubject<Boolean> exbolusCheckSubject = BehaviorSubject.create();
    private BehaviorSubject<Boolean> basalCheckSubject = BehaviorSubject.create();


    @Inject
    public PauseBasalTask() {
        super(TaskFunc.PAUSE_BASAL);

        BASAL_PAUSE = new BasalPause();
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

    public Single<PatchBooleanResponse> pause(float pauseDurationHour, long pausedTimestamp, @Nullable AlarmCode alarmCode) {
        PatchState patchState = pm.getPatchState();

        if(patchState.isNormalBasalPaused())
            return Single.just(new PatchBooleanResponse(true));

        enqueue(TaskFunc.UPDATE_CONNECTION);

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
                .concatMapSingle(v -> getSuspendedTime(pausedTimestamp, alarmCode))
                .concatMapSingle(suspendedTimestamp -> pauseBasal(pauseDurationHour, suspendedTimestamp, alarmCode))
                .firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, e.getMessage()));
    }

    private Single<Long> getSuspendedTime(long pausedTimestamp, @Nullable AlarmCode alarmCode) {
        return Single.just(pausedTimestamp);
    }

    private Single<Long> stopNowBolus(long pausedTimestamp, boolean isNowBolusActive) {
        if (isNowBolusActive) {
            return BOLUS_STOP.stop(IPatchConstant.NOW_BOLUS_ID)
                       .doOnSuccess(this::checkResponse)
                       .doOnSuccess(v -> onNowBolusStopped(v.getInjectedBolusAmount(), pausedTimestamp))
                       .map(v -> pausedTimestamp);
        }

        return Single.just(pausedTimestamp);
    }

    private Single<Long> stopExtBolus(long pausedTimestamp, boolean isExtBolusActive) {
        if (isExtBolusActive) {
            return BOLUS_STOP.stop(IPatchConstant.EXT_BOLUS_ID)
                       .doOnSuccess(this::checkResponse)
                       .doOnSuccess(v -> onExtBolusStopped(v.getInjectedBolusAmount(), pausedTimestamp))
                       .map(v -> pausedTimestamp);
        }

        return Single.just(pausedTimestamp);
    }

    private Single<Long> stopTempBasal(long pausedTimestamp, boolean isTempBasalActive) {
        if (isTempBasalActive) {
            return TEMP_BASAL_SCHEDULE_STOP.stop()
                       .doOnSuccess(this::checkResponse)
                       .doOnSuccess(v -> onTempBasalCanceled(pausedTimestamp))
                       .map(v -> pausedTimestamp);
        }

        return Single.just(pausedTimestamp);
    }

    private Single<PatchBooleanResponse> pauseBasal(float pauseDurationHour, long suspendedTimestamp, @Nullable AlarmCode alarmCode) {
        if(alarmCode == null)  {
            return BASAL_PAUSE.pause(pauseDurationHour)
                    .doOnSuccess(this::checkResponse)
                    .doOnSuccess(v -> onBasalPaused(pauseDurationHour, suspendedTimestamp, null));
        }

        // 정지 알람 발생 시 basal pause 커맨드 전달하지 않음 - 주입 정지 이력만 생성
        onBasalPaused(pauseDurationHour, suspendedTimestamp, alarmCode);

        return Single.just(new PatchBooleanResponse(true));
    }

    private void onBasalPaused(float pauseDurationHour, long suspendedTimestamp, @Nullable AlarmCode alarmCode) {
        if (!pm.getNormalBasalManager().isSuspended()) {
            String strCode = (alarmCode != null) ? alarmCode.name() : null;

            if (alarmCode != null) {
                pm.getPatchConfig().updateNormalBasalPausedSilently();
            }
            else {
                pm.getPatchConfig().updateNormalBasalPaused(pauseDurationHour);
            }
            pm.getNormalBasalManager().updateBasalSuspended();

            pm.flushNormalBasalManager();
            pm.flushPatchConfig();

            if((alarmCode == null || alarmCode.getType() == AlarmCode.TYPE_ALERT) && pauseDurationHour != 0)
                alarmRegistry.add(AlarmCode.B001, TimeUnit.MINUTES.toMillis((long)(pauseDurationHour * 60)), false).subscribe();
        }

        enqueue(TaskFunc.UPDATE_CONNECTION);
    }

    private void onNowBolusStopped(int injectedBolusAmount, long suspendedTimestamp) {
        updateNowBolusStopped(injectedBolusAmount, suspendedTimestamp);
    }

    private void onExtBolusStopped(int injectedBolusAmount, long suspendedTimestamp) {
        updateExtBolusStopped(injectedBolusAmount, suspendedTimestamp);
    }

    private void onTempBasalCanceled(long suspendedTimestamp) {
        TempBasal tempBasal = pm.getTempBasalManager().getStartedBasal();

        if (tempBasal != null) {
            pm.getTempBasalManager().updateBasalStopped();
            pm.flushTempBasalManager();
        }
    }

    public synchronized void enqueue(float pauseDurationHour, long pausedTime, @Nullable AlarmCode alarmCode) {
        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = pause(pauseDurationHour, pausedTime, alarmCode)
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe(v -> {
                    bolusCheckSubject.onNext(false);
                    exbolusCheckSubject.onNext(false);
                    basalCheckSubject.onNext(false);
                });
        }
    }
}
