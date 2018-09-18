package info.nightscout.androidaps.plugins.general.automation.triggers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.R;

public class TriggerOr extends Trigger {

    private List<Trigger> list = new ArrayList<>();

    @Override
    synchronized boolean shouldRun() {
        boolean result = false;

        for (Trigger t : list) {
            result = result || t.shouldRun();
        }
        return result;
    }

    @Override
    synchronized String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", TriggerOr.class.getName());
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

    @Override
    int friendlyName() {
        return R.string.or;
    }

    @Override
    String friendlyDescription() {
        int counter = 0;
        StringBuilder result = new StringBuilder();
        for (Trigger t : list) {
            if (counter++ > 0) result.append(R.string.or);
            result.append(t.friendlyDescription());
        }
        return result.toString();
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
