package info.nightscout.androidaps.plugins.general.automation.actions;

import android.support.annotation.StringRes;
import android.widget.LinearLayout;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.plugins.general.automation.elements.InputBg;
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration;
import info.nightscout.androidaps.plugins.general.automation.elements.Label;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DateUtil;

public class ActionStartTempTarget extends Action {
    private String reason;
    private double valueInMgdl;
    private String units;
    private int durationInMinutes;

    private InputBg inputBg;
    private InputDuration inputDuration;

    public ActionStartTempTarget() {
        units = Constants.MGDL;
    }

    public ActionStartTempTarget(String units) {
        this.units = Constants.MGDL;
    }

    @Override
    public int friendlyName() {
        return R.string.starttemptarget;
    }

    @Override
    void doAction(Callback callback) {
        TempTarget tempTarget = new TempTarget().date(DateUtil.now()).duration(durationInMinutes).reason(reason).source(Source.USER).low(valueInMgdl).high(valueInMgdl);
        TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        if (callback != null)
            callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
    }

    @Override
    public void generateDialog(LinearLayout root) {
        inputBg = new InputBg(units);
        if (valueInMgdl != 0d) inputBg.setMgdl(valueInMgdl);
        inputDuration = new InputDuration(durationInMinutes, InputDuration.TimeUnit.MINUTES);

        int unitResId = units.equals(Constants.MGDL) ? R.string.mgdl : R.string.mmol;
        Label labelBg = new Label(MainApp.gs(R.string.careportal_newnstreatment_percentage_label), MainApp.gs(unitResId), inputBg);
        labelBg.generateDialog(root);

        Label labelDuration = new Label(MainApp.gs(R.string.careportal_newnstreatment_duration_min_label), "min", inputDuration);
        labelDuration.generateDialog(root);
    }

    @Override
    public void saveFromDialog() {
        valueInMgdl = inputBg.getMgdl();
        durationInMinutes = inputDuration.getMinutes();
    }
}
