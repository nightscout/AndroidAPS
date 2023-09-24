package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.SetKey;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BaseResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.NormalBasal;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchLifecycleEvent;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

@Singleton
public class ActivateTask extends TaskBase {
    @Inject StartNormalBasalTask startBasalTask;

    private final SetKey SET_KEY = new SetKey();

    @Inject
    public ActivateTask() {
        super(TaskFunc.ACTIVATE);
    }

    public Single<Boolean> start() {
        NormalBasal enabled = pm.getNormalBasalManager().getNormalBasal();
        return isReady()
                .concatMapSingle(v -> SET_KEY.setKey())
                .doOnNext(this::checkResponse)
                .firstOrError()
                .observeOn(Schedulers.io())
                .flatMap(v -> startBasalTask.start(enabled))
                .doOnSuccess(this::onActivated)
                .map(BaseResponse::isSuccess)
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "ActivateTask error"));
    }

    private void onActivated(BaseResponse response) {
        pm.updatePatchLifeCycle(PatchLifecycleEvent.createActivated());
    }
}
