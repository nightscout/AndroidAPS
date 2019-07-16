package info.nightscout.androidaps.plugins.general.automation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.general.automation.actions.Action;
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector;

public class AutomationEvent {

    private Trigger trigger = new TriggerConnector();
    private List<Action> actions = new ArrayList<>();
    private String title;

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
            // trigger
            o.put("trigger", trigger.toJSON());
            // actions
            JSONArray array = new JSONArray();
            for (Action a : actions) {
                array.put(a.toJSON());
            }
            o.put("actions", array);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }

    public AutomationEvent fromJSON(String data) {
        try {
            JSONObject d = new JSONObject(data);
            // title
            title = d.optString("title", "");
            // trigger
            trigger = Trigger.instantiate(d.getString("trigger"));
            // actions
            JSONArray array = d.getJSONArray("actions");
            actions.clear();
            for (int i = 0; i < array.length(); i++) {
                actions.add(Action.instantiate(new JSONObject(array.getString(i))));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }
}
