package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.InfoReminderSet;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchBooleanResponse;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class InfoReminderTask extends TaskBase {
    @Inject IPreferenceManager pm;

    private final InfoReminderSet INFO_REMINDER_SET;

    @Inject
    public InfoReminderTask() {
        super(TaskFunc.INFO_REMINDER);
        INFO_REMINDER_SET = new InfoReminderSet();
    }

    /* alert delay 사용안함 */
    public Single<PatchBooleanResponse> set(boolean infoReminder) {
        return isReady()
                .concatMapSingle(v -> INFO_REMINDER_SET.set(infoReminder))
                .doOnNext(this::checkResponse)
                .firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "InfoReminderTask error"));
    }

    public synchronized void enqueue() {

        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = set(pm.getPatchConfig().getInfoReminder())
                    .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                    .subscribe();
        }
    }
}
