package info.nightscout.androidaps.plugins.general.automation.actions;

import org.json.JSONException;
import org.json.JSONObject;

abstract class Trigger {

    protected static final int ISLOWER = -2;
    protected static final int ISEQUALORLOWER = -1;
    protected static final int ISEQUAL = 0;
    protected static final int ISEQUALORGREATER = 1;
    protected static final int ISGREATER = 2;

    protected static final int NOTAVAILABLE = 10;

    Trigger() {
    }

    abstract boolean shouldRun();

    abstract String toJSON();

    abstract Trigger fromJSON(String data);

    void notifyAboutRun(long time) {
    }

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
