package info.nightscout.androidaps.plugins.general.automation.actions;

import android.widget.LinearLayout;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.automation.elements.InputString;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.JsonHelper;

public class ActionSendSMS extends Action {

    public InputString text = new InputString();

    @Override
    public int friendlyName() {
        return R.string.sendsmsactiondescription;
    }

    @Override
    public String shortDescription() {
        return MainApp.gs(R.string.sendsmsactionlabel, text.getValue());
    }

    @Override
    public void doAction(Callback callback) {
        boolean result = SmsCommunicatorPlugin.getPlugin().sendNotificationToAllNumbers(text.getValue());
        if (callback != null)
            callback.result(new PumpEnactResult().success(result).comment(result ? R.string.ok : R.string.danar_error)).run();

    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_notifications);
    }

    @Override
    public String toJSON() {
        JSONObject o = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            data.put("text", text.getValue());
            o.put("type", this.getClass().getName());
            o.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }

    @Override
    public Action fromJSON(String data) {
        try {
            JSONObject o = new JSONObject(data);
            text.setValue(JsonHelper.safeGetString(o, "text"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public boolean hasDialog() {
        return true;
    }

    @Override
    public void generateDialog(LinearLayout root) {

        new LayoutBuilder()
                .add(new LabelWithElement(MainApp.gs(R.string.sendsmsactiontext), "", text))
                .build(root);
    }

}
