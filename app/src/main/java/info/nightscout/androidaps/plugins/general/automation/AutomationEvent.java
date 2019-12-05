package info.nightscout.androidaps.plugins.general.automation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.general.automation.actions.Action;
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutomationEvent {
    private static final Logger log = LoggerFactory.getLogger(AutomationEvent.class);

    private Trigger trigger = new TriggerConnector();
    private List<Action> actions = new ArrayList<>();
    private String title;
    private boolean enabled = true;

    public void setTitle(String title) {
        this.title = title;
    }

    public void setTrigger(Trigger trigger) {
        this.trigger = trigger;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public List<Action> getActions() {
        return actions;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean newState) {
        enabled = newState;
    }

    public TriggerConnector getPreconditions() {
        TriggerConnector trigger = new TriggerConnector(TriggerConnector.Type.AND);
        for (Action action : actions) {
            if (action.precondition != null)
                trigger.add(action.precondition);
        }
        return trigger;
    }

    public void addAction(Action action) {
        actions.add(action);
    }

    public String getTitle() {
        return title;
    }

    public String toJSON() {
        JSONObject o = new JSONObject();
        try {
            // title
            o.put("title", title);
            o.put("enabled", enabled);
            // trigger
            o.put("trigger", trigger.toJSON());
            // actions
            JSONArray array = new JSONArray();
            for (Action a : actions) {
                array.put(a.toJSON());
            }
            o.put("actions", array);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return o.toString();
    }

    public AutomationEvent fromJSON(String data) {
        try {
            JSONObject d = new JSONObject(data);
            title = d.optString("title", "");
            enabled = d.optBoolean("enabled", true);
            trigger = Trigger.instantiate(d.getString("trigger"));
            JSONArray array = d.getJSONArray("actions");
            actions.clear();
            for (int i = 0; i < array.length(); i++) {
                actions.add(Action.instantiate(new JSONObject(array.getString(i))));
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return this;
    }
}
