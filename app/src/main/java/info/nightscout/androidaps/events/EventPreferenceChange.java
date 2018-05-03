package info.nightscout.androidaps.events;

import info.nightscout.androidaps.MainApp;

/**
 * Created by mike on 19.06.2016.
 */
public class EventPreferenceChange extends Event {
    public String changedKey;
    public EventPreferenceChange(String key) {
        changedKey = key;
    }

    public EventPreferenceChange(int resourceID) {
        changedKey = MainApp.gs(resourceID);
    }

    public boolean isChanged(int id) {
        return changedKey.equals(MainApp.gs(id));
    }

    public boolean isChanged(String id) {
        return changedKey.equals(id);
    }
}
