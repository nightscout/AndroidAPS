package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.alarm.IAlarmRegistry;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetErrorCodes;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.AeCodeResponse;
import info.nightscout.rx.bus.RxBus;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class FetchAlarmTask extends TaskBase {
    @Inject RxBus rxBus;
    @Inject IAlarmRegistry alarmRegistry;

    private final GetErrorCodes ALARM_ALERT_ERROR_CODE_GET;

    @Inject
    public FetchAlarmTask() {
        super(TaskFunc.FETCH_ALARM);
        ALARM_ALERT_ERROR_CODE_GET = new GetErrorCodes();
    }

    public Single<AeCodeResponse> getPatchAlarm() {
        return isReady()
                .concatMapSingle(v -> ALARM_ALERT_ERROR_CODE_GET.get())
                .doOnNext(this::checkResponse)
                .firstOrError()
                .doOnSuccess(aeCodeResponse -> alarmRegistry.add(aeCodeResponse.getAlarmCodes()))
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "FetchAlarmTask error"));
    }

    public synchronized void enqueue() {
        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = getPatchAlarm()
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe();
        }
    }
}
