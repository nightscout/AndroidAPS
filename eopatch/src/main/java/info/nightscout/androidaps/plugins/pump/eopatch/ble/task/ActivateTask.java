package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BasalScheduleSetBig;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.SetKey;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BaseResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.vo.NormalBasal;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchLifecycleEvent;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class ActivateTask extends TaskBase {
    @Inject StartNormalBasalTask startBasalTask;

    private SetKey SET_KEY;
    private BasalScheduleSetBig BASAL_SCHEDULE_SET_BIG;


    @Inject
    public ActivateTask() {
        super(TaskFunc.ACTIVATE);
        SET_KEY = new SetKey();
        BASAL_SCHEDULE_SET_BIG = new BasalScheduleSetBig();
    }

    public Single<Boolean> start() {
        NormalBasal enabled = pm.getNormalBasalManager().getNormalBasal();
        return isReady()
                .concatMapSingle(v -> SET_KEY.setKey())
                .doOnNext(this::checkResponse)
                .firstOrError()
                .observeOn(Schedulers.io()).doOnSuccess(this::onActivated)
                .flatMap(v -> startBasalTask.start(enabled, false))
                .map(BaseResponse::isSuccess)
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, e.getMessage()));
    }

    private void onActivated(BaseResponse response) {
        pm.updatePatchLifeCycle(PatchLifecycleEvent.createActivated());
    }
}
