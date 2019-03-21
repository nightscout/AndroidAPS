package info.nightscout.androidaps.plugins.general.automation.actions;

import android.widget.LinearLayout;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.queue.Callback;

public abstract class Action {

    public abstract int friendlyName();

    abstract void doAction(Callback callback);

    public void generateDialog(LinearLayout root) { }

    public boolean hasDialog() { return false; }

    public String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", this.getClass().getName());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }

    public abstract Optional<Integer> icon();

    public void copy(Action action) { }

    /*package*/ Action fromJSON(String data) {
        return this;
    }

    public static Action instantiate(JSONObject object) {
        try {
            String type = object.getString("type");
            JSONObject data = object.getJSONObject("data");
            Class clazz = Class.forName(type);
            return ((Action) clazz.newInstance()).fromJSON(data.toString());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}
