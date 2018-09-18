package info.nightscout.androidaps.plugins.general.automation.actions;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DateUtil;

public class ActionStartTempTarget extends Action {

    double value;
    int durationInMinutes;
    String reason;
    String units = Constants.MGDL;

    @Override
    int friendlyName() {
        return R.string.starttemptarget;
    }

    @Override
    void doAction(Callback callback) {
        double converted = Profile.toMgdl(value, units);
        TempTarget tempTarget = new TempTarget().date(DateUtil.now()).duration(durationInMinutes).reason(reason).source(Source.USER).low(converted).high(converted);
        TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        if (callback != null)
            callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
    }
}
