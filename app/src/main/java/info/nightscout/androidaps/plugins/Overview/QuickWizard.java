package info.nightscout.androidaps.plugins.Overview;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.DateUtil;

/**
 * Created by mike on 12.10.2016.
 */

public class QuickWizard {

    public class QuickWizardEntry {
        public JSONObject storage;
        public int position;

        /*
            {
                buttonText: "Meal",
                carbs: 36,
                validFrom: 8 * 60 * 60, // seconds from midnight
                validTo: 9 * 60 * 60,   // seconds from midnight
            }
         */
        public QuickWizardEntry() {
            String emptyData = "{\"buttonText\":\"\",\"carbs\":0,\"validFrom\":0,\"validTo\":86340}";
            try {
                storage = new JSONObject(emptyData);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            position = -1;
        }

        public QuickWizardEntry(JSONObject entry, int position) {
            storage = entry;
            this.position = position;
        }

        public Boolean isActive() {
            return NSProfile.secondsFromMidnight() >= validFrom() && NSProfile.secondsFromMidnight() <= validTo();
        }

        public String buttonText() {
            try {
                return storage.getString("buttonText");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return "";
        }

        public Integer carbs() {
            try {
                return storage.getInt("carbs");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return 0;
        }

        public Date validFromDate() {
            return DateUtil.toDate(validFrom());
        }

        public Date validToDate() {
            return DateUtil.toDate(validTo());
        }

        public Integer validFrom() {
            try {
                return storage.getInt("validFrom");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return 0;
        }

        public Integer validTo() {
            try {
                return storage.getInt("validTo");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return 0;
        }

    }

    JSONArray storage = new JSONArray();

    public void setData(JSONArray newData) {
        storage = newData;
    }

    public void save() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("QuickWizard", storage.toString());
        editor.apply();
    }

    public int size() {
        return storage.length();
    }

    public QuickWizardEntry get(int position) {
        try {
            return new QuickWizardEntry((JSONObject) storage.get(position), position);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Boolean isActive() {
        for (int i = 0; i < storage.length(); i++) {
            try {
                if (new QuickWizardEntry((JSONObject) storage.get(i), i).isActive()) return true;
            } catch (JSONException e) {
                e.printStackTrace();
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
                e.printStackTrace();
            }
        }
        save();
    }

    public void remove(int position) {
        storage.remove(position);
        save();
    }
}
