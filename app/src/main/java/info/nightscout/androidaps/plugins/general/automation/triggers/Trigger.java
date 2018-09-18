package info.nightscout.androidaps.plugins.general.automation.triggers;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.R;

public abstract class Trigger {

    protected static final int ISLOWER = -2;
    protected static final int ISEQUALORLOWER = -1;
    protected static final int ISEQUAL = 0;
    protected static final int ISEQUALORGREATER = 1;
    protected static final int ISGREATER = 2;

    protected static final int ISNOTAVAILABLE = 10;

    Trigger() {
    }

    abstract boolean shouldRun();

    abstract String toJSON();

    abstract Trigger fromJSON(String data);

    abstract int friendlyName();

    abstract String friendlyDescription();

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

    public static int toComparatorString(int comparator) {
        switch (comparator) {
            case ISLOWER:
                return R.string.islower;
            case ISEQUALORLOWER:
                return R.string.isequalorlower;
            case ISEQUAL:
                return R.string.isequal;
            case ISEQUALORGREATER:
                return R.string.isequalorgreater;
            case ISGREATER:
                return R.string.isgreater;
            case ISNOTAVAILABLE:
                return R.string.isnotavailable;
        }
        return R.string.unknown;
    }
}
