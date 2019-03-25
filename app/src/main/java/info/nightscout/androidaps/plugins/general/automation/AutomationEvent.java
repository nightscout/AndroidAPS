package info.nightscout.androidaps.plugins.general.automation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.general.automation.actions.Action;
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;

public class AutomationEvent implements Cloneable {

    private Trigger trigger;
    private List<Action> actions = new ArrayList<>();
    private String title;

    public void setTitle(String title) { this.title = title; }

    public void setTrigger(Trigger trigger) { this.trigger = trigger; }

    public Trigger getTrigger() {
        return trigger;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void addAction(Action action) { actions.add(action); }

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
            for (int i = 0; i < array.length(); i++) {
                actions.add(Action.instantiate(new JSONObject(array.getString(i))));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    public void apply(AutomationEvent event) {
        trigger = event.trigger;
        actions = event.actions;
        title = event.title;
    }

    @Override
    public AutomationEvent clone() throws CloneNotSupportedException {
        AutomationEvent e = (AutomationEvent) super.clone();
        e.title = title;

        // clone actions
        e.actions = new ArrayList<>();
        for(Action a : actions) {
            e.actions.add(a.clone());
        }

        // clone triggers
        if (trigger != null) {
            e.trigger = trigger.clone();
        }
        return e;
    }
}
