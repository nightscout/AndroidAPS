package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;


import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
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
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode;
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.IAlarmRegistry;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BasalPause;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchBooleanResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

@Singleton
public class PauseBasalTask extends BolusTask {
    @Inject IAlarmRegistry alarmRegistry;
    @Inject IPreferenceManager pm;
    @Inject CommandQueue commandQueue;
    @Inject AAPSLogger aapsLogger;
    @Inject PumpSync pumpSync;
    @Inject UserEntryLogger uel;

    private final BasalPause BASAL_PAUSE;

    private final BehaviorSubject<Boolean> bolusCheckSubject = BehaviorSubject.create();
    private final BehaviorSubject<Boolean> extendedBolusCheckSubject = BehaviorSubject.create();
    private final BehaviorSubject<Boolean> basalCheckSubject = BehaviorSubject.create();

    @Inject
    public PauseBasalTask() {
        super(TaskFunc.PAUSE_BASAL);

        BASAL_PAUSE = new BasalPause();
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

    public Single<PatchBooleanResponse> pause(float pauseDurationHour, long pausedTimestamp, @Nullable AlarmCode alarmCode) {
        PatchState patchState = pm.getPatchState();

        if (patchState.isNormalBasalPaused())
            return Single.just(new PatchBooleanResponse(true));

        enqueue(TaskFunc.UPDATE_CONNECTION);

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
                .concatMapSingle(v -> getSuspendedTime(pausedTimestamp))
                .concatMapSingle(suspendedTimestamp -> pauseBasal(pauseDurationHour, alarmCode))
                .firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "PauseBasalTask error"));
    }

    private Single<Long> getSuspendedTime(long pausedTimestamp) {
        return Single.just(pausedTimestamp);
    }

    private Single<PatchBooleanResponse> pauseBasal(float pauseDurationHour, @Nullable AlarmCode alarmCode) {
        if (alarmCode == null) {
            return BASAL_PAUSE.pause(pauseDurationHour)
                    .doOnSuccess(this::checkResponse)
                    .doOnSuccess(v -> onBasalPaused(pauseDurationHour, null));
        }

        // 정지 알람 발생 시 basal pause 커맨드 전달하지 않음 - 주입 정지 이력만 생성
        onBasalPaused(pauseDurationHour, alarmCode);

        return Single.just(new PatchBooleanResponse(true));
    }

    private void onBasalPaused(float pauseDurationHour, @Nullable AlarmCode alarmCode) {
        if (!pm.getNormalBasalManager().isSuspended()) {
            if (alarmCode != null) {
                pm.getPatchConfig().updateNormalBasalPausedSilently();
            } else {
                pm.getPatchConfig().updateNormalBasalPaused(pauseDurationHour);
            }
            pm.getNormalBasalManager().updateBasalSuspended();

            pm.flushNormalBasalManager();
            pm.flushPatchConfig();

            if ((alarmCode == null || alarmCode.getType() == AlarmCode.TYPE_ALERT) && pauseDurationHour != 0)
                alarmRegistry.add(AlarmCode.B001, TimeUnit.MINUTES.toMillis((long) (pauseDurationHour * 60)), false).subscribe();
        }

        enqueue(TaskFunc.UPDATE_CONNECTION);
    }

    public synchronized void enqueue(float pauseDurationHour, long pausedTime, @Nullable AlarmCode alarmCode) {
        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = pause(pauseDurationHour, pausedTime, alarmCode)
                    .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                    .subscribe(v -> {
                        bolusCheckSubject.onNext(false);
                        extendedBolusCheckSubject.onNext(false);
                        basalCheckSubject.onNext(false);
                    });
        }
    }
}
