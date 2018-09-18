package info.nightscout.androidaps.plugins.general.automation.actions;

import org.json.JSONException;
import org.json.JSONObject;

abstract class Trigger {

    Trigger() {
    }

    Trigger(String js) {
        fromJSON(js);
    }

    abstract boolean shouldRun();
    abstract String toJSON();
    abstract Trigger fromJSON(String data);

    static Trigger instantiate(JSONObject object) {
        try {
            String type = object.getString("type");
            String data = object.getString("data");
            Class clazz = Class.forName(type);
            return ((Trigger) clazz.newInstance()).fromJSON(data);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | JSONException e) {
            e.printStackTrace();
        }
        return null;

     }

}
