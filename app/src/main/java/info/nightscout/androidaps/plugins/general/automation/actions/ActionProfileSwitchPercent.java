package info.nightscout.androidaps.plugins.general.automation.actions;

import android.widget.LinearLayout;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration;
import info.nightscout.androidaps.plugins.general.automation.elements.InputPercent;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerProfilePercent;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.JsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActionProfileSwitchPercent extends Action {
    private static final Logger log = LoggerFactory.getLogger(ActionProfileSwitchPercent.class);

    InputPercent pct = new InputPercent();
    InputDuration duration = new InputDuration(0, InputDuration.TimeUnit.MINUTES);

    public ActionProfileSwitchPercent() {
        precondition = new TriggerProfilePercent().comparator(Comparator.Compare.IS_EQUAL).setValue(100);
    }

    @Override
    public int friendlyName() {
        return R.string.profilepercentage;
    }

    @Override
    public String shortDescription() {
        if (duration.getMinutes() == 0)
            return MainApp.gs(R.string.startprofileforever, (int) pct.getValue());
        else
            return MainApp.gs(R.string.startprofile, (int) pct.getValue(), duration.getMinutes());
    }

    @Override
    public void doAction(Callback callback) {
        ProfileFunctions.doProfileSwitch((int) duration.getValue(), (int) pct.getValue(), 0);
        if (callback != null)
            callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
    }

    @Override
    public void generateDialog(LinearLayout root) {
        new LayoutBuilder()
                .add(new LabelWithElement(MainApp.gs(R.string.percent_u), "", pct))
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
            o.put("type", ActionProfileSwitchPercent.class.getName());
            JSONObject data = new JSONObject();
            data.put("percentage", pct.getValue());
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
            pct.setValue(JsonHelper.safeGetInt(d, "percentage"));
            duration.setMinutes(JsonHelper.safeGetInt(d, "durationInMinutes"));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return this;
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.icon_actions_profileswitch);
    }
}
