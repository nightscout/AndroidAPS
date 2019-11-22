package info.nightscout.androidaps.plugins.general.automation.actions;

import android.widget.LinearLayout;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.JsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActionLoopSuspend extends Action {
    private static final Logger log = LoggerFactory.getLogger(ActionLoopSuspend.class);

    public InputDuration minutes = new InputDuration(0, InputDuration.TimeUnit.MINUTES);

    @Override
    public int friendlyName() {
        return R.string.suspendloop;
    }

    @Override
    public String shortDescription() {
        return MainApp.gs(R.string.suspendloopforXmin, minutes.getMinutes());
    }

    @Override
    public void doAction(Callback callback) {
        if (!LoopPlugin.getPlugin().isSuspended()) {
            LoopPlugin.getPlugin().suspendLoop(minutes.getMinutes());
            RxBus.INSTANCE.send(new EventRefreshOverview("ActionLoopSuspend"));
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
        } else {
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.alreadysuspended)).run();
        }
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_pause_circle_outline_24dp);
    }

    @Override
    public String toJSON() {
        JSONObject o = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            data.put("minutes", minutes.getMinutes());
            o.put("type", this.getClass().getName());
            o.put("data", data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return o.toString();
    }

    @Override
    public Action fromJSON(String data) {
        try {
            JSONObject o = new JSONObject(data);
            minutes.setMinutes(JsonHelper.safeGetInt(o, "minutes"));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
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
                .add(new LabelWithElement(MainApp.gs(R.string.careportal_newnstreatment_duration_min_label), "", minutes))
                .build(root);
    }

}
