package info.nightscout.androidaps.plugins.general.automation.actions;

import android.widget.LinearLayout;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.plugins.general.automation.elements.InputBg;
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration;
import info.nightscout.androidaps.plugins.general.automation.elements.Label;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;

public class ActionStartTempTarget extends Action {
    private String reason = "";
    private InputBg value;
    private InputDuration duration = new InputDuration(0, InputDuration.TimeUnit.MINUTES);

    public ActionStartTempTarget() {
        value = new InputBg(Constants.MGDL);
    }

    public ActionStartTempTarget(String units) {
        value = new InputBg(units);
    }

    @Override
    public int friendlyName() {
        return R.string.starttemptarget;
    }

    @Override
    void doAction(Callback callback) {
        TempTarget tempTarget = new TempTarget().date(DateUtil.now()).duration((int)duration.getMinutes()).reason(reason).source(Source.USER).low(value.getMgdl()).high(value.getMgdl());
        TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        if (callback != null)
            callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
    }

    @Override
    public void generateDialog(LinearLayout root) {
        int unitResId = value.getUnits().equals(Constants.MGDL) ? R.string.mgdl : R.string.mmol;

        new LayoutBuilder()
            .add(new Label(MainApp.gs(R.string.careportal_newnstreatment_percentage_label), MainApp.gs(unitResId), value))
            .add(new Label(MainApp.gs(R.string.careportal_newnstreatment_duration_min_label), "min", duration))
            .build(root);
    }

    @Override
    public boolean hasDialog() {
        return true;
    }

    @Override
    public String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", ActionStartTempTarget.class.getName());
            JSONObject data = new JSONObject();
            data.put("reason", reason);
            data.put("valueInMg", value.getMgdl());
            data.put("units", value.getUnits());
            data.put("durationInMinutes", duration.getMinutes());
            o.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }

    @Override
    public Action fromJSON(String data) {
        try {
            JSONObject d = new JSONObject(data);
            reason = JsonHelper.safeGetString(d, "reason");
            value.setUnits(JsonHelper.safeGetString(d, "units"));
            value.setMgdl(JsonHelper.safeGetInt(d, "valueInMg"));
            duration.setMinutes(JsonHelper.safeGetDouble(d, "durationInMinutes"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.icon_cp_cgm_target);
    }
}
