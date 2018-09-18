package info.nightscout.androidaps.plugins.general.automation.actions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TriggerAnd extends Trigger {

    private List<Trigger> list = new ArrayList<>();

    @Override
    synchronized boolean shouldRun() {
        boolean result = true;

        for (Trigger t : list) {
            result = result && t.shouldRun();
        }
        return result;
    }

    @Override
    synchronized String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", TriggerAnd.class.getName());
            JSONArray array = new JSONArray();
            for (Trigger t : list) {
                array.put(t.toJSON());
            }
            o.put("data", array.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }

    @Override
    Trigger fromJSON(String data) {
        try {
            JSONArray array = new JSONArray(data);
            for (int i = 0; i < array.length(); i++) {
                Trigger newItem = instantiate(new JSONObject(array.getString(i)));
                list.add(newItem);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    synchronized void add(Trigger t) {
        list.add(t);
    }

    synchronized boolean remove(Trigger t) {
        return list.remove(t);
    }

    int size() {
        return list.size();
    }

    Trigger get(int i) {
        return list.get(i);
    }
}
