package info.nightscout.androidaps.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 12.10.2016.
 */

public class QuickWizard {
    private static Logger log = LoggerFactory.getLogger(QuickWizard.class);

    private JSONArray storage = new JSONArray();

    public void setData(JSONArray newData) {
        storage = newData;
    }

    public void save() {
        SP.putString("QuickWizard", storage.toString());
    }

    public int size() {
        return storage.length();
    }

    public QuickWizardEntry get(int position) {
        try {
            return new QuickWizardEntry((JSONObject) storage.get(position), position);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    public Boolean isActive() {
        for (int i = 0; i < storage.length(); i++) {
            try {
                if (new QuickWizardEntry((JSONObject) storage.get(i), i).isActive()) return true;
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
        return false;
    }

    public QuickWizardEntry getActive() {
        for (int i = 0; i < storage.length(); i++) {
            QuickWizardEntry entry;
            try {
                entry = new QuickWizardEntry((JSONObject) storage.get(i), i);
            } catch (JSONException e) {
                continue;
            }
            if (entry.isActive()) return entry;
        }
        return null;
    }

    public QuickWizardEntry newEmptyItem() {
        return new QuickWizardEntry();
    }

    public void addOrUpdate(QuickWizardEntry newItem) {
        if (newItem.position == -1)
            storage.put(newItem.storage);
        else {
            try {
                storage.put(newItem.position, newItem.storage);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
        save();
    }

    public void remove(int position) {
        storage.remove(position);
        save();
    }
}
