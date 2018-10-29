package info.nightscout.androidaps.plugins.general.automation.actions;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.queue.Callback;

public abstract class Action {

    abstract int friendlyName();

    abstract void doAction(Callback callback);

    public String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", this.getClass().getName());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }

    public static Action instantiate(JSONObject object) {
        try {
            String type = object.getString("type");
            Class clazz = Class.forName(type);
            return (Action) clazz.newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}
