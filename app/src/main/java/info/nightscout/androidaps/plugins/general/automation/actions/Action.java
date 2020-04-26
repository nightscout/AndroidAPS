package info.nightscout.androidaps.plugins.general.automation.actions;

import android.widget.LinearLayout;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;

import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;
import info.nightscout.androidaps.queue.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
    Action ideas:

    * cancel temp target
    * change preference setting
    * enable/disable plugin
    * create notification
    * create ugly alarm
    * create profile switch
    * set/cancel tbr
    * set/cancel extended bolus
    * run bolus wizard

    Trigger ideas:

    * location (close to)
    * connected to specific wifi
    * internet available/not available
    * nsclient connected/disconnected
    * iob
    * cob
    * autosens value
    * delta, short delta, long delta
    * last bolus ago
    * is tbr running
    * bolus wizard result
    * loop is enabled, disabled, suspended, running

*/


public abstract class Action {
    private static final Logger log = LoggerFactory.getLogger(Action.class);

    public Trigger precondition = null;

    public abstract int friendlyName();

    public abstract String shortDescription();

    public abstract void doAction(Callback callback);

    public void generateDialog(LinearLayout root) {
    }

    public boolean hasDialog() {
        return false;
    }

    public String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", this.getClass().getName());
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return o.toString();
    }

    public abstract Optional<Integer> icon();

    public Action fromJSON(String data) {
        return this;
    }

    @Nullable
    public static Action instantiate(JSONObject object) {
        try {
            String type = object.getString("type");
            JSONObject data = object.optJSONObject("data");
            Class clazz = Class.forName(type);
            return ((Action) clazz.newInstance()).fromJSON(data != null ? data.toString() : "");
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | JSONException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    public void apply(Action a) {
        try {
            JSONObject object = new JSONObject(a.toJSON());
            String type = object.getString("type");
            JSONObject data = object.getJSONObject("data");
            if (type.equals(getClass().getName())) {
                fromJSON(data.toString());
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }
}
