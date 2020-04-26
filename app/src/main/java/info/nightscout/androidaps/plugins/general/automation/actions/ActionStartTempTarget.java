package info.nightscout.androidaps.plugins.general.automation.actions;

import android.widget.LinearLayout;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.plugins.general.automation.elements.ComparatorExists;
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration;
import info.nightscout.androidaps.plugins.general.automation.elements.InputTempTarget;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTempTarget;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActionStartTempTarget extends Action {
    private static final Logger log = LoggerFactory.getLogger(ActionStartTempTarget.class);

    String reason = "";
    InputTempTarget value = new InputTempTarget();
    InputDuration duration = new InputDuration(0, InputDuration.TimeUnit.MINUTES);
    private TempTarget tempTarget;

    public ActionStartTempTarget() {
        precondition = new TriggerTempTarget().comparator(ComparatorExists.Compare.NOT_EXISTS);
    }

    @Override
    public int friendlyName() {
        return R.string.starttemptarget;
    }

    @Override
    public String shortDescription() {
        tempTarget = new TempTarget()
                .date(DateUtil.now())
                .duration(duration.getMinutes())
                .reason(reason)
                .source(Source.USER)
                .low(Profile.toMgdl(value.getValue(), value.getUnits()))
                .high(Profile.toMgdl(value.getValue(), value.getUnits()));
        return MainApp.gs(R.string.starttemptarget) + ": " + (tempTarget == null ? "null" : tempTarget.friendlyDescription(value.getUnits()));
    }

    @Override
    public void doAction(Callback callback) {
        tempTarget = new TempTarget()
                .date(DateUtil.now())
                .duration(duration.getMinutes())
                .reason(reason)
                .source(Source.USER)
                .low(Profile.toMgdl(value.getValue(), value.getUnits()))
                .high(Profile.toMgdl(value.getValue(), value.getUnits()));
        TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        if (callback != null)
            callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
    }

    @Override
    public void generateDialog(LinearLayout root) {
        int unitResId = value.getUnits().equals(Constants.MGDL) ? R.string.mgdl : R.string.mmol;

        new LayoutBuilder()
                .add(new LabelWithElement(MainApp.gs(R.string.careportal_temporarytarget) + "\n[" + MainApp.gs(unitResId) + "]", "", value))
                .add(new LabelWithElement(MainApp.gs(R.string.careportal_newnstreatment_duration_min_label), "", duration))
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
            data.put("value", value.getValue());
            data.put("units", value.getUnits());
            data.put("durationInMinutes", duration.getMinutes());
            o.put("data", data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return o.toString();
    }

    @Override
    public Action fromJSON(String data) {
        try {
            JSONObject d = new JSONObject(data);
            reason = JsonHelper.safeGetString(d, "reason");
            value.setUnits(JsonHelper.safeGetString(d, "units"));
            value.setValue(JsonHelper.safeGetDouble(d, "value"));
            duration.setMinutes(JsonHelper.safeGetInt(d, "durationInMinutes"));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return this;
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.icon_cp_cgm_target);
    }
}
