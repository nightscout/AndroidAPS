package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.PatchStateManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.UpdateConnection;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.UpdateConnectionResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class UpdateConnectionTask extends TaskBase {
    @Inject PatchStateManager patchStateManager;

    private final UpdateConnection UPDATE_CONNECTION;

    @Inject
    public UpdateConnectionTask() {
        super(TaskFunc.UPDATE_CONNECTION);

        UPDATE_CONNECTION = new UpdateConnection();
    }

    public Single<PatchState> update() {
        return isReady().concatMapSingle(v -> updateJob()).firstOrError();
    }

    public Single<PatchState> updateJob() {
        return UPDATE_CONNECTION.get()
                .doOnSuccess(this::checkResponse)
                .map(UpdateConnectionResponse::getPatchState)
                .map(bytes -> PatchState.Companion.create(bytes, System.currentTimeMillis()))
                .doOnSuccess(state -> onUpdateConnection(state))
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "UpdateConnectionTask error"));
    }

    private void onUpdateConnection(PatchState patchState) {
        patchStateManager.updatePatchState(patchState);
    }

    public synchronized void enqueue() {
        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = update()
                    .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                    .subscribe();
        }
    }
}
